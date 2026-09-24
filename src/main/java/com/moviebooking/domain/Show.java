package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shows", indexes = {
        @Index(name = "idx_show_screen_time", columnList = "screen_id, start_time"),
        @Index(name = "idx_show_movie", columnList = "movie_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_tier_id")
    private PricingTier pricingTier;

    /** Optional. Falls back to the default refund policy when null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_policy_id")
    private RefundPolicy refundPolicy;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowStatus status = ShowStatus.SCHEDULED;
}
