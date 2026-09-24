package com.moviebooking.repository;

import com.moviebooking.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBookingIdOrderByIdAsc(Long bookingId);
}
