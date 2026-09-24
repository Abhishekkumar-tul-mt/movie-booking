package com.moviebooking.repository;

import com.moviebooking.domain.Booking;
import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.ShowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Serialises pay / cancel / expire on the same booking. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByShowIdOrderByCreatedAtDesc(Long showId);

    @Query("select b.id from Booking b where b.status = :status and b.holdExpiresAt <= :now")
    List<Long> findExpiredIds(@Param("status") BookingStatus status, @Param("now") LocalDateTime now);

    @Query("select b.id from Booking b where b.show.id = :showId and b.status in :statuses")
    List<Long> findIdsByShowAndStatuses(@Param("showId") Long showId,
                                        @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select b.id from Booking b where b.status = :status and b.reminderSent = false "
            + "and b.show.status = :showStatus and b.show.startTime > :now and b.show.startTime <= :until")
    List<Long> findDueForReminder(@Param("status") BookingStatus status,
                                  @Param("showStatus") ShowStatus showStatus,
                                  @Param("now") LocalDateTime now,
                                  @Param("until") LocalDateTime until);

    @Query("select count(b) from Booking b where b.user.id = :userId and b.discountCode = :code "
            + "and b.status in :statuses")
    long countUserDiscountUsage(@Param("userId") Long userId,
                                @Param("code") String code,
                                @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select sum(b.totalAmount) from Booking b where b.show.id = :showId and b.status = :status")
    BigDecimal sumTotalByShowAndStatus(@Param("showId") Long showId, @Param("status") BookingStatus status);
}
