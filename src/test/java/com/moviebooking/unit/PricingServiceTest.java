package com.moviebooking.unit;

import com.moviebooking.domain.PricingTier;
import com.moviebooking.domain.SeatType;
import com.moviebooking.service.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService service = new PricingService(null);

    // 2026-09-23 is a Wednesday, 2026-09-26 a Saturday, 2026-09-27 a Sunday
    private static final LocalDateTime WEEKDAY = LocalDateTime.of(2026, 9, 23, 18, 0);
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 9, 26, 18, 0);
    private static final LocalDateTime SUNDAY = LocalDateTime.of(2026, 9, 27, 11, 0);

    private PricingTier tier(String weekendMultiplier) {
        PricingTier t = new PricingTier();
        t.setName("Standard");
        t.setRegularPrice(new BigDecimal("200.00"));
        t.setPremiumPrice(new BigDecimal("350.00"));
        t.setWeekendMultiplier(new BigDecimal(weekendMultiplier));
        return t;
    }

    @Test
    void weekdayUsesBasePricePerSeatType() {
        assertThat(service.priceFor(SeatType.REGULAR, tier("1.25"), WEEKDAY)).isEqualByComparingTo("200.00");
        assertThat(service.priceFor(SeatType.PREMIUM, tier("1.25"), WEEKDAY)).isEqualByComparingTo("350.00");
    }

    @Test
    void weekendAppliesMultiplierOnSaturdayAndSunday() {
        assertThat(service.priceFor(SeatType.REGULAR, tier("1.25"), SATURDAY)).isEqualByComparingTo("250.00");
        assertThat(service.priceFor(SeatType.PREMIUM, tier("1.25"), SUNDAY)).isEqualByComparingTo("437.50");
    }

    @Test
    void multiplierOfOneMeansNoWeekendSurcharge() {
        assertThat(service.priceFor(SeatType.REGULAR, tier("1.00"), SATURDAY)).isEqualByComparingTo("200.00");
    }

    @Test
    void resultIsRoundedToTwoDecimals() {
        PricingTier t = tier("1.33");
        t.setRegularPrice(new BigDecimal("199.99"));
        assertThat(service.priceFor(SeatType.REGULAR, t, SATURDAY).scale()).isEqualTo(2);
        assertThat(service.priceFor(SeatType.REGULAR, t, SATURDAY)).isEqualByComparingTo("265.99");
    }
}
