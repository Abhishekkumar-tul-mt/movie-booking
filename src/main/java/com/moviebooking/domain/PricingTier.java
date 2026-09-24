package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A named price card. A show references one tier:
 * price = (PREMIUM ? premiumPrice : regularPrice) * (weekend ? weekendMultiplier : 1).
 */
@Entity
@Table(name = "pricing_tiers")
@Getter
@Setter
@NoArgsConstructor
public class PricingTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal regularPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premiumPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weekendMultiplier = BigDecimal.ONE;
}
