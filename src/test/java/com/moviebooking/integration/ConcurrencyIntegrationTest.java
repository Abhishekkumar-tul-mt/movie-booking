package com.moviebooking.integration;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.PaymentMethod;
import com.moviebooking.domain.PaymentStatus;
import com.moviebooking.domain.PaymentType;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.exception.SeatUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the core guarantee: however many customers race, a seat is never double-allocated.
 * All threads are released simultaneously through a latch to maximise contention.
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    private static boolean isConflict(Throwable t) {
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof SeatUnavailableException || x instanceof ObjectOptimisticLockingFailureException
                    || x instanceof PessimisticLockingFailureException) {
                return true;
            }
        }
        return false;
    }

    /** Runs all tasks at the same instant and returns their futures (already completed). */
    private <T> List<Future<T>> runSimultaneously(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("no deadlock / hang").isTrue();
        return futures;
    }

    @Test
    void manyCustomersRacingForOneSeatExactlyOneWins() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        Long seat = f.regularSeatIds().get(0);
        int contenders = 20;
        List<UserResponse> users = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            users.add(createCustomer());
        }

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (UserResponse u : users) {
            tasks.add(() -> {
                try {
                    bookingService.createHold(u.id(), new HoldRequest(f.showId(), List.of(seat), null));
                    return true;
                } catch (Exception e) {
                    assertThat(isConflict(e)).as("unexpected failure type: " + e).isTrue();
                    return false;
                }
            });
        }
        int winners = 0;
        for (Future<Boolean> future : runSimultaneously(tasks)) {
            if (future.get()) {
                winners++;
            }
        }

        assertThat(winners).isEqualTo(1);
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(bookingRepository.findByShowIdOrderByCreatedAtDesc(f.showId())).hasSize(1);
    }

    @Test
    void overlappingMultiSeatRequestsNeverDoubleAllocateOrDeadlock() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        List<Long> pool4 = f.regularSeatIds().subList(0, 4);
        int contenders = 12;
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            UserResponse u = createCustomer();
            // each customer wants 2 of the same 4 seats, requested in a random order
            List<Long> pair = new ArrayList<>(pool4);
            Collections.shuffle(pair);
            List<Long> wanted = new ArrayList<>(pair.subList(0, 2));
            tasks.add(() -> {
                try {
                    bookingService.createHold(u.id(), new HoldRequest(f.showId(), wanted, null));
                    return true;
                } catch (Exception e) {
                    assertThat(isConflict(e)).as("unexpected failure type: " + e).isTrue();
                    return false;
                }
            });
        }
        int winners = 0;
        for (Future<Boolean> future : runSimultaneously(tasks)) {
            if (future.get()) {
                winners++;
            }
        }

        // at most 2 disjoint pairs fit in 4 seats; every held seat belongs to exactly one winner
        assertThat(winners).isBetween(1, 2);
        assertThat(showSeatRepository.countByShowIdAndStatus(f.showId(), ShowSeatStatus.HELD)).isEqualTo(winners * 2L);
        assertThat(bookingRepository.findByShowIdOrderByCreatedAtDesc(f.showId())).hasSize(winners);
    }

    @Test
    void concurrentPaymentsForTheSameBookingChargeOnlyOnce() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        BookingResponse held = hold(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)), null);

        List<Callable<BookingStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> bookingService.pay(c.id(), held.id(), new PaymentRequest(PaymentMethod.CARD, "tok_ok"))
                    .status());
        }
        for (Future<BookingStatus> future : runSimultaneously(tasks)) {
            assertThat(future.get()).isEqualTo(BookingStatus.CONFIRMED);
        }

        long charges = paymentRepository.findByBookingIdOrderByIdAsc(held.id()).stream()
                .filter(p -> p.getPaymentType() == PaymentType.CHARGE && p.getStatus() == PaymentStatus.SUCCESS)
                .count();
        assertThat(charges).isEqualTo(1);
    }

    @Test
    void bookedSeatsStayBookedUnderLoad() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        Long seat = f.regularSeatIds().get(0);
        UserResponse owner = createCustomer();
        holdAndPayViaService(owner, f.showId(), seat);

        AtomicInteger successes = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UserResponse u = createCustomer();
            tasks.add(() -> {
                try {
                    bookingService.createHold(u.id(), new HoldRequest(f.showId(), List.of(seat), null));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    assertThat(isConflict(e)).isTrue();
                }
                return null;
            });
        }
        for (Future<Void> future : runSimultaneously(tasks)) {
            future.get();
        }
        assertThat(successes.get()).isZero();
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.BOOKED);
    }

    private void holdAndPayViaService(UserResponse user, Long showId, Long seat) {
        BookingResponse held = bookingService.createHold(user.id(), new HoldRequest(showId, List.of(seat), null));
        bookingService.pay(user.id(), held.id(), new PaymentRequest(PaymentMethod.CARD, "tok_ok"));
    }
}
