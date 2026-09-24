package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "discount_codes")
@Getter
@Setter
@NoArgsConstructor
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always stored upper-case. */
    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    /** Percentage (0-100) for PERCENTAGE, absolute amount for FLAT. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Cap for PERCENTAGE discounts. */
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column(nullable = false)
    private LocalDateTime validTo;

    /** Global redemption cap; null = unlimited. */
    private Integer usageLimit;

    /** Per-customer cap; null = unlimited. */
    private Integer perUserLimit;

    @Column(nullable = false)
    private int usedCount = 0;

    @Column(nullable = false)
    private boolean active = true;
}
