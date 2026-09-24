package com.moviebooking.repository;

import com.moviebooking.domain.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
    List<Theater> findByCityIdOrderByName(Long cityId);
}
