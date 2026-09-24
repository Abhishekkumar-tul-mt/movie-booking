package com.moviebooking.repository;

import com.moviebooking.domain.Screen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    List<Screen> findByTheaterIdOrderByName(Long theaterId);

    boolean existsByTheaterIdAndNameIgnoreCase(Long theaterId, String name);
}
