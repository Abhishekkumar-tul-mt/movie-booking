package com.moviebooking.repository;

import com.moviebooking.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByBookingIdOrderByIdAsc(Long bookingId);
}
