package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "theater_id")
    private Theater theater;

    @Column(nullable = false)
    private int totalSeats;

    public Screen(String name, Theater theater, int totalSeats) {
        this.name = name;
        this.theater = theater;
        this.totalSeats = totalSeats;
    }
}
