package com.moviebooking.integration;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.DiscountType;
import com.moviebooking.domain.NotificationType;
import com.moviebooking.domain.PaymentStatus;
import com.moviebooking.domain.PaymentType;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.PricingDtos.DiscountCodeRequest;
import com.moviebooking.dto.PricingDtos.RefundQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundAndDiscountIntegrationTest extends IntegrationTestBase {

    /** Policy in the fixture: >=48h 100%, >=24h 75%, >=6h 50%, otherwise 0%. Ticket price: 2 x 200 = 400. */
    private void assertRefund(long hoursBeforeShow, String expectedRefund, int expectedPercent) throws Exception {
        Fixture f = createShowFixture(hoursFromNow(hoursBeforeShow));
        UserResponse c = createCustomer();
        List<Long> seats = f.regularSeatIds().subList(0, 2);
        BookingResponse paid = holdAndPay(c.email(), f.showId(), seats);

        RefundQuote quote = read(get("/api/bookings/" + paid.id() + "/refund-quote", c.email())
                .andExpect(status().isOk()), RefundQuote.class);
        assertThat(quote.refundPercent()).isEqualTo(expectedPercent);
        assertThat(quote.refundAmount()).isEqualByComparingTo(expectedRefund);

        BookingResponse cancelled = read(post("/api/bookings/" + paid.id() + "/cancel", c.email(), null)
                .andExpect(status().isOk()), BookingResponse.class);
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.refundAmount()).isEqualByComparingTo(expectedRefund);

        boolean refundIssued = new BigDecimal(expectedRefund).signum() > 0;
        long refunds = cancelled.payments().stream()
                .filter(p -> p.paymentType() == PaymentType.REFUND && p.status() == PaymentStatus.SUCCESS).count();
        assertThat(refunds).isEqualTo(refundIssued ? 1 : 0);

        // seats go back on sale
        for (Long seat : seats) {
            assertThat(showSeatRepository.findById(seat).orElseThrow().getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findByBookingIdOrderByIdAsc(paid.id()).stream()
                        .map(n -> n.getNotificationType()).toList())
                        .contains(NotificationType.BOOKING_CANCELLATION));

        // cancelling again is idempotent and does not refund twice
        BookingResponse again = read(post("/api/bookings/" + paid.id() + "/cancel", c.email(), null)
                .andExpect(status().isOk()), BookingResponse.class);
        assertThat(again.payments()).hasSameSizeAs(cancelled.payments());
    }

    @Test
    void fullRefundWhenCancelledFarInAdvance() throws Exception {
        assertRefund(72, "400.00", 100);
    }

    @Test
    void partialRefundInTheMiddleTier() throws Exception {
        assertRefund(30, "300.00", 75);
    }

    @Test
    void noRefundTooCloseToTheShow() throws Exception {
        assertRefund(3, "0.00", 0);
    }

    @Test
    void cannotCancelAConfirmedBookingAfterShowStart() throws Exception {
        Fixture f = createShowFixture(hoursFromNow(72));
        UserResponse c = createCustomer();
        BookingResponse paid = holdAndPay(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)));
        // push the show into the past
        jdbc.update("update shows set start_time = ?, end_time = ? where id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusHours(3)),
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusHours(1)), f.showId());

        post("/api/bookings/" + paid.id() + "/cancel", c.email(), null).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------------------------- discounts

    private String createFlatCode(UserResponse admin, String value, String minOrder, Integer usageLimit,
                                  Integer perUserLimit) throws Exception {
        String code = "T" + uniq().toUpperCase();
        LocalDateTime now = LocalDateTime.now(clock);
        post("/api/admin/discount-codes", admin.email(), new DiscountCodeRequest(code, DiscountType.FLAT,
                new BigDecimal(value), null, minOrder == null ? null : new BigDecimal(minOrder),
                now.minusDays(1), now.plusDays(1), usageLimit, perUserLimit, true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(code));
        return code;
    }

    @Test
    void discountCodeReducesTotalAndIsRedeemedOnPayment() throws Exception {
        UserResponse admin = createAdmin();
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));
        String code = createFlatCode(admin, "50", "300", 5, null);

        BookingResponse held = hold(c.email(), f.showId(), f.regularSeatIds().subList(0, 2), code.toLowerCase());
        assertThat(held.subtotal()).isEqualByComparingTo("400.00");
        assertThat(held.discountAmount()).isEqualByComparingTo("50.00");
        assertThat(held.totalAmount()).isEqualByComparingTo("350.00");
        assertThat(discountCodeRepository.findByCode(code).orElseThrow().getUsedCount()).isZero(); // not yet

        BookingResponse paid = pay(c.email(), held.id(), "tok_ok");
        assertThat(paid.payments().get(0).amount()).isEqualByComparingTo("350.00");
        assertThat(discountCodeRepository.findByCode(code).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void discountValidationRulesAreEnforcedOnHold() throws Exception {
        UserResponse admin = createAdmin();
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));

        // unknown code
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), List.of(f.regularSeatIds().get(0)), "NOPE"))
                .andExpect(status().isBadRequest());
        // below minimum order (one 200 seat vs minimum 300)
        String minCode = createFlatCode(admin, "50", "300", null, null);
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), List.of(f.regularSeatIds().get(1)), minCode))
                .andExpect(status().isBadRequest());
        // the failed attempts must not have left seats held
        assertThat(showSeatRepository.findById(f.regularSeatIds().get(0)).orElseThrow().getStatus())
                .isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    @Test
    void perUserLimitStopsRepeatUseOfTheSameCode() throws Exception {
        UserResponse admin = createAdmin();
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));
        String code = createFlatCode(admin, "20", null, null, 1);

        hold(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)), code);
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), List.of(f.regularSeatIds().get(1)), code))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hundredPercentDiscountConfirmsWithoutCharging() throws Exception {
        UserResponse admin = createAdmin();
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));
        String code = createFlatCode(admin, "1000", null, null, null); // flat 1000 capped at subtotal

        BookingResponse held = hold(c.email(), f.showId(), List.of(f.regularSeatIds().get(0)), code);
        assertThat(held.totalAmount()).isEqualByComparingTo("0.00");
        BookingResponse paid = pay(c.email(), held.id(), "tok_ok");
        assertThat(paid.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paid.payments()).isEmpty();
    }

    // ------------------------------------------------------------------------------ admin show cancel

    @Test
    void adminCancellingAShowRefundsEveryoneInFull() throws Exception {
        UserResponse admin = createAdmin();
        Fixture f = createShowFixture(hoursFromNow(3)); // policy would give 0% - show cancellation ignores it
        UserResponse c1 = createCustomer();
        UserResponse c2 = createCustomer();
        BookingResponse confirmed = holdAndPay(c1.email(), f.showId(), f.regularSeatIds().subList(0, 2));
        BookingResponse pending = hold(c2.email(), f.showId(), List.of(f.regularSeatIds().get(2)), null);

        post("/api/admin/shows/" + f.showId() + "/cancel", admin.email(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingsCancelled").value(2));

        BookingResponse r1 = read(get("/api/bookings/" + confirmed.id(), c1.email()), BookingResponse.class);
        assertThat(r1.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(r1.refundAmount()).isEqualByComparingTo("400.00");
        BookingResponse r2 = read(get("/api/bookings/" + pending.id(), c2.email()), BookingResponse.class);
        assertThat(r2.status()).isEqualTo(BookingStatus.CANCELLED);

        // no new bookings on a cancelled show
        UserResponse c3 = createCustomer();
        post("/api/bookings", c3.email(), new HoldRequest(f.showId(), List.of(f.regularSeatIds().get(5)), null))
                .andExpect(status().isConflict());
        // cancelling twice is rejected
        post("/api/admin/shows/" + f.showId() + "/cancel", admin.email(), null).andExpect(status().isConflict());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationRepository.findByBookingIdOrderByIdAsc(confirmed.id()).stream()
                        .map(n -> n.getNotificationType()).toList())
                        .contains(NotificationType.SHOW_CANCELLED));
    }
}
