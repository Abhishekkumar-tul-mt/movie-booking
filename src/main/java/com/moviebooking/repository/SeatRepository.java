package com.moviebooking.repository;

import com.moviebooking.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByScreenIdOrderByRowLabelAscSeatNumberAsc(Long screenId);
}
