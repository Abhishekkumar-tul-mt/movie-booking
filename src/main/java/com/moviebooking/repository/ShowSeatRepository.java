package com.moviebooking.repository;

import com.moviebooking.domain.ShowSeat;
import com.moviebooking.domain.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * Row-locks the requested seats (SELECT ... FOR UPDATE). Ordered by id so concurrent transactions that
     * lock overlapping seat sets always acquire locks in the same order - no deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.id in :ids and ss.show.id = :showId order by ss.id")
    List<ShowSeat> lockByIdsAndShow(@Param("ids") Collection<Long> ids, @Param("showId") Long showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.id in :ids order by ss.id")
    List<ShowSeat> lockByIds(@Param("ids") Collection<Long> ids);

    @Query("select ss from ShowSeat ss join fetch ss.seat st where ss.show.id = :showId "
            + "order by st.rowLabel, st.seatNumber")
    List<ShowSeat> findByShowIdWithSeat(@Param("showId") Long showId);

    long countByShowIdAndStatus(Long showId, ShowSeatStatus status);
}
