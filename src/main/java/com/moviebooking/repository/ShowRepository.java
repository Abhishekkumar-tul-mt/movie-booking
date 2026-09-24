package com.moviebooking.repository;

import com.moviebooking.domain.Show;
import com.moviebooking.domain.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ShowRepository extends JpaRepository<Show, Long>, JpaSpecificationExecutor<Show> {

    /** Number of live shows on the screen whose [start, end) window intersects [rangeStart, rangeEnd). */
    @Query("select count(s) from Show s where s.screen.id = :screenId and s.status = :status "
            + "and s.startTime < :rangeEnd and s.endTime > :rangeStart")
    long countOverlapping(@Param("screenId") Long screenId,
                          @Param("status") ShowStatus status,
                          @Param("rangeStart") LocalDateTime rangeStart,
                          @Param("rangeEnd") LocalDateTime rangeEnd);
}
