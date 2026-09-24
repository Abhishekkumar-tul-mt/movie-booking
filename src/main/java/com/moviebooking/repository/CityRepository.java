package com.moviebooking.repository;

import com.moviebooking.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
    boolean existsByNameIgnoreCase(String name);
}
