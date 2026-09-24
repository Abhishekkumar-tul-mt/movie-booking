package com.moviebooking.dto;

import com.moviebooking.domain.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PricingDtos {
    private PricingDtos() {
    }

    public record PricingTierRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.00") BigDecimal regularPrice,
            @NotNull @DecimalMin("0.00") BigDecimal premiumPrice,
            @NotNull @DecimalMin("1.00") BigDecimal weekendMultiplier) {
    }

    public record PricingTierResponse(Long id, String name, BigDecimal regularPrice, BigDecimal premiumPrice,
                                      BigDecimal weekendMultiplier) {
    }

    public record RefundRuleDto(@Min(0) int minHoursBeforeShow, @Min(0) @Max(100) int refundPercent) {
    }

    public record RefundPolicyRequest(
            @NotBlank @Size(max = 100) String name,
            boolean defaultPolicy,
            @NotEmpty @Valid List<RefundRuleDto> rules) {
    }

    public record RefundPolicyResponse(Long id, String name, boolean defaultPolicy, List<RefundRuleDto> rules) {
    }

    public record DiscountCodeRequest(
            @NotBlank @Size(max = 32) String code,
            @NotNull DiscountType discountType,
            @NotNull @DecimalMin("0.01") BigDecimal discountValue,
            @DecimalMin("0.01") BigDecimal maxDiscountAmount,
            @DecimalMin("0.00") BigDecimal minOrderAmount,
            @NotNull LocalDateTime validFrom,
            @NotNull LocalDateTime validTo,
            @Min(1) Integer usageLimit,
            @Min(1) Integer perUserLimit,
            Boolean active) {
    }

    public record DiscountCodeResponse(Long id, String code, DiscountType discountType, BigDecimal discountValue,
                                       BigDecimal maxDiscountAmount, BigDecimal minOrderAmount,
                                       LocalDateTime validFrom, LocalDateTime validTo, Integer usageLimit,
                                       Integer perUserLimit, int usedCount, boolean active) {
    }

    public record RefundQuote(Long bookingId, BigDecimal paidAmount, int refundPercent, BigDecimal refundAmount,
                              String policyName, long minutesBeforeShow) {
    }
}
