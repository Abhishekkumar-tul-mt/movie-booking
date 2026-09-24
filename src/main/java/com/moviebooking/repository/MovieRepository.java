package com.moviebooking.repository;

import com.moviebooking.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByActiveTrueOrderByTitle();
}
