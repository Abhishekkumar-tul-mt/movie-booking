package com.moviebooking.unit;

import com.moviebooking.domain.DiscountCode;
import com.moviebooking.domain.DiscountType;
import com.moviebooking.exception.BadRequestException;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.service.DiscountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscountServiceTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 24, 12, 0);
    private DiscountCodeRepository repository;
    private BookingRepository bookingRepository;
    private DiscountService service;

    @BeforeEach
    void setUp() {
        repository = mock(DiscountCodeRepository.class);
        bookingRepository = mock(BookingRepository.class);
        service = new DiscountService(repository, bookingRepository);
    }

    private DiscountCode code(DiscountType type, String value) {
        DiscountCode d = new DiscountCode();
        d.setCode("SAVE");
        d.setDiscountType(type);
        d.setDiscountValue(new BigDecimal(value));
        d.setValidFrom(now.minusDays(1));
        d.setValidTo(now.plusDays(1));
        d.setActive(true);
        return d;
    }

    @Test
    void percentageDiscountIsCappedByMaxAmount() {
        DiscountCode d = code(DiscountType.PERCENTAGE, "10");
        assertThat(service.computeAmount(d, new BigDecimal("400.00"))).isEqualByComparingTo("40.00");
        d.setMaxDiscountAmount(new BigDecimal("30.00"));
        assertThat(service.computeAmount(d, new BigDecimal("400.00"))).isEqualByComparingTo("30.00");
    }

    @Test
    void flatDiscountNeverExceedsSubtotal() {
        DiscountCode d = code(DiscountType.FLAT, "500");
        assertThat(service.computeAmount(d, new BigDecimal("400.00"))).isEqualByComparingTo("400.00");
    }

    @Test
    void evaluateNormalisesCodeAndReturnsDiscount() {
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(code(DiscountType.FLAT, "50")));
        DiscountService.Result r = service.evaluate("  save ", new BigDecimal("400"), now, 1L);
        assertThat(r.code()).isEqualTo("SAVE");
        assertThat(r.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void unknownCodeIsRejected() {
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.evaluate("NOPE", BigDecimal.TEN, now, 1L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Invalid");
    }

    @Test
    void expiredInactiveAndBelowMinimumCodesAreRejected() {
        DiscountCode expired = code(DiscountType.FLAT, "50");
        expired.setValidTo(now.minusHours(1));
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.evaluate("SAVE", new BigDecimal("400"), now, 1L))
                .isInstanceOf(BadRequestException.class);

        DiscountCode inactive = code(DiscountType.FLAT, "50");
        inactive.setActive(false);
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.evaluate("SAVE", new BigDecimal("400"), now, 1L))
                .isInstanceOf(BadRequestException.class);

        DiscountCode minimum = code(DiscountType.FLAT, "50");
        minimum.setMinOrderAmount(new BigDecimal("500"));
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(minimum));
        assertThatThrownBy(() -> service.evaluate("SAVE", new BigDecimal("400"), now, 1L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Minimum");
    }

    @Test
    void globalAndPerUserLimitsAreEnforced() {
        DiscountCode exhausted = code(DiscountType.FLAT, "50");
        exhausted.setUsageLimit(10);
        exhausted.setUsedCount(10);
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(exhausted));
        assertThatThrownBy(() -> service.evaluate("SAVE", new BigDecimal("400"), now, 1L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("limit");

        DiscountCode perUser = code(DiscountType.FLAT, "50");
        perUser.setPerUserLimit(1);
        when(repository.findByCode("SAVE")).thenReturn(Optional.of(perUser));
        when(bookingRepository.countUserDiscountUsage(anyLong(), anyString(), anyCollection())).thenReturn(1L);
        assertThatThrownBy(() -> service.evaluate("SAVE", new BigDecimal("400"), now, 7L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("maximum");
    }
}
