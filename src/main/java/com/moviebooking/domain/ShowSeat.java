package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The bookable unit: one physical seat for one show, with its price snapshot and state.
 * <p>
 * Concurrency: rows are locked with SELECT ... FOR UPDATE (in id order, to avoid deadlocks) before any
 * state change; the unique (show, seat) constraint and the @Version column are additional safety nets.
 */
@Entity
@Table(name = "show_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"show_id", "seat_id"}),
        indexes = @Index(name = "idx_show_seat_show_status", columnList = "show_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id")
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowSeatStatus status = ShowSeatStatus.AVAILABLE;

    /** Set while HELD. */
    private LocalDateTime holdExpiresAt;

    /** The booking currently holding / owning this seat. Null when AVAILABLE. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Version
    private long version;

    public ShowSeat(Show show, Seat seat, BigDecimal price) {
        this.show = show;
        this.seat = seat;
        this.price = price;
    }

    /** Available, or held by a hold that has already expired (lazy expiry - the sweeper may not have run yet). */
    public boolean isAvailableAt(LocalDateTime now) {
        if (status == ShowSeatStatus.AVAILABLE) {
            return true;
        }
        return status == ShowSeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }

    public void hold(Booking newBooking, LocalDateTime expiresAt) {
        this.status = ShowSeatStatus.HELD;
        this.booking = newBooking;
        this.holdExpiresAt = expiresAt;
    }

    public void markBooked() {
        this.status = ShowSeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public void release() {
        this.status = ShowSeatStatus.AVAILABLE;
        this.booking = null;
        this.holdExpiresAt = null;
    }
}
