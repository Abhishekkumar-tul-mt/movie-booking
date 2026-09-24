package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A physical seat in a screen's layout. */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "row_label", "seat_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id")
    private Screen screen;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    public Seat(Screen screen, String rowLabel, int seatNumber, SeatType seatType) {
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.seatType = seatType;
    }

    public String label() {
        return rowLabel + seatNumber;
    }
}
