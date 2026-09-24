package com.moviebooking.integration;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.Notification;
import com.moviebooking.domain.NotificationType;
import com.moviebooking.domain.PaymentStatus;
import com.moviebooking.domain.PaymentType;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowIntegrationTest extends IntegrationTestBase {

    private List<NotificationType> notificationTypes(Long bookingId) {
        return notificationRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
                .map(Notification::getNotificationType).toList();
    }

    @Test
    void customerHoldsPaysAndGetsAsyncConfirmation() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();

        BookingResponse held = hold(c.email(), f.showId(), f.regularSeatIds().subList(0, 2), null);
        assertThat(held.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(held.totalAmount()).isEqualByComparingTo("400.00");
        assertThat(held.seats()).hasSize(2);
        assertThat(held.holdExpiresAt()).isAfter(java.time.LocalDateTime.now(clock));
        get("/api/shows/" + f.showId() + "/seats", c.email())
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(10));

        BookingResponse paid = pay(c.email(), held.id(), "tok_ok");
        assertThat(paid.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paid.confirmedAt()).isNotNull();
        assertThat(paid.payments()).hasSize(1);
        assertThat(paid.payments().get(0).status()).isEqualTo(PaymentStatus.SUCCESS);

        // notification is delivered asynchronously, after commit
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationTypes(held.id())).contains(NotificationType.BOOKING_CONFIRMATION));
        get("/api/notifications", c.email()).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMATION"));

        // booking history
        get("/api/bookings", c.email()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        // paying again is idempotent: no second charge
        BookingResponse again = pay(c.email(), held.id(), "tok_ok");
        assertThat(again.payments()).hasSize(1);
    }

    @Test
    void secondCustomerCannotTakeAHeldSeat() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c1 = createCustomer();
        UserResponse c2 = createCustomer();
        Long seat = f.regularSeatIds().get(0);

        hold(c1.email(), f.showId(), List.of(seat), null);

        post("/api/bookings", c2.email(), new HoldRequest(f.showId(), List.of(seat), null))
                .andExpect(status().isConflict());
        // a different seat is still fine
        hold(c2.email(), f.showId(), List.of(f.regularSeatIds().get(1)), null);
    }

    @Test
    void paymentFailureKeepsTheHoldAndAllowsRetry() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        BookingResponse held = hold(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)), null);

        post("/api/bookings/" + held.id() + "/payment", c.email(),
                new com.moviebooking.dto.BookingDtos.PaymentRequest(com.moviebooking.domain.PaymentMethod.CARD, "fail_card"))
                .andExpect(status().isPaymentRequired());

        BookingResponse afterFailure = read(get("/api/bookings/" + held.id(), c.email())
                .andExpect(status().isOk()), BookingResponse.class);
        assertThat(afterFailure.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(afterFailure.payments()).hasSize(1);
        assertThat(afterFailure.payments().get(0).status()).isEqualTo(PaymentStatus.FAILED);

        BookingResponse retried = pay(c.email(), held.id(), "tok_ok");
        assertThat(retried.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(retried.payments()).hasSize(2);
    }

    @Test
    void expiredHoldCannotBePaidAndSeatIsReleased() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        Long seat = f.regularSeatIds().get(0);
        BookingResponse held = hold(c.email(), f.showId(), List.of(seat), null);

        backdateHold(held.id());

        post("/api/bookings/" + held.id() + "/payment", c.email(),
                new com.moviebooking.dto.BookingDtos.PaymentRequest(com.moviebooking.domain.PaymentMethod.CARD, "tok_ok"))
                .andExpect(status().isGone());

        assertThat(bookingRepository.findById(held.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationTypes(held.id())).contains(NotificationType.HOLD_EXPIRED));
    }

    @Test
    void sweeperReleasesExpiredHoldsAutomatically() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        Long seat = f.regularSeatIds().get(0);
        BookingResponse held = hold(c.email(), f.showId(), List.of(seat), null);
        backdateHold(held.id());

        assertThat(holdExpiryService.sweep()).isGreaterThanOrEqualTo(1);

        assertThat(bookingRepository.findById(held.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        post("/api/bookings/" + held.id() + "/payment", c.email(),
                new com.moviebooking.dto.BookingDtos.PaymentRequest(com.moviebooking.domain.PaymentMethod.CARD, "tok_ok"))
                .andExpect(status().isConflict());
        // another customer can now book it
        UserResponse c2 = createCustomer();
        hold(c2.email(), f.showId(), List.of(seat), null);
    }

    @Test
    void expiredHoldCanBeTakenOverAndSweeperDoesNotReleaseTheNewOwnersSeat() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c1 = createCustomer();
        UserResponse c2 = createCustomer();
        Long seat = f.regularSeatIds().get(0);
        BookingResponse first = hold(c1.email(), f.showId(), List.of(seat), null);
        backdateHold(first.id());

        // seat map already reports it free (lazy expiry) and c2 can take it before the sweeper runs
        BookingResponse second = hold(c2.email(), f.showId(), List.of(seat), null);
        holdExpiryService.sweep();

        assertThat(bookingRepository.findById(first.id()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.HELD);
        assertThat(pay(c2.email(), second.id(), "tok_ok").status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancellingAPendingBookingReleasesSeatsWithoutRefund() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        Long seat = f.regularSeatIds().get(0);
        BookingResponse held = hold(c.email(), f.showId(), List.of(seat), null);

        BookingResponse cancelled = read(post("/api/bookings/" + held.id() + "/cancel", c.email(), null)
                .andExpect(status().isOk()), BookingResponse.class);

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.refundAmount()).isNull();
        assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void customersOnlySeeTheirOwnBookings() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse owner = createCustomer();
        UserResponse other = createCustomer();
        BookingResponse b = hold(owner.email(), f.showId(), List.of(f.regularSeatIds().get(0)), null);

        get("/api/bookings/" + b.id(), other.email()).andExpect(status().isNotFound());
        post("/api/bookings/" + b.id() + "/cancel", other.email(), null).andExpect(status().isNotFound());
        get("/api/bookings", other.email()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reminderIsQueuedExactlyOnceForUpcomingShows() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(1)); // inside the 120 minute reminder window
        UserResponse c = createCustomer();
        BookingResponse paid = holdAndPay(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)));

        assertThat(reminderService.sendDueReminders()).isGreaterThanOrEqualTo(1);
        assertThat(bookingService.sendReminder(paid.id())).isFalse(); // already sent

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationTypes(paid.id())).contains(NotificationType.SHOW_REMINDER));
        long reminders = notificationTypes(paid.id()).stream().filter(t -> t == NotificationType.SHOW_REMINDER).count();
        assertThat(reminders).isEqualTo(1);
    }

    @Test
    void chargeIsRecordedAsALedgerEntry() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        BookingResponse paid = holdAndPay(c.email(), f.showId(), f.premiumSeatIds().subList(0, 1));

        assertThat(paid.totalAmount()).isEqualByComparingTo("350.00");
        assertThat(paymentRepository.findByBookingIdOrderByIdAsc(paid.id()))
                .hasSize(1)
                .allSatisfy(p -> {
                    assertThat(p.getPaymentType()).isEqualTo(PaymentType.CHARGE);
                    assertThat(p.getAmount()).isEqualByComparingTo("350.00");
                });
    }
}
