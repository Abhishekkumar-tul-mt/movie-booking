package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Immutable snapshot of one seat inside a booking (kept for history even after the seat is released). */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id")
    private ShowSeat showSeat;

    @Column(nullable = false)
    private String seatLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public BookingItem(Booking booking, ShowSeat showSeat, String seatLabel, SeatType seatType, BigDecimal price) {
        this.booking = booking;
        this.showSeat = showSeat;
        this.seatLabel = seatLabel;
        this.seatType = seatType;
        this.price = price;
    }
}
