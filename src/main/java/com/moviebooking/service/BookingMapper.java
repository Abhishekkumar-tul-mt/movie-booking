package com.moviebooking.service;

import com.moviebooking.domain.Booking;
import com.moviebooking.domain.Show;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.BookingSeatResponse;
import com.moviebooking.dto.BookingDtos.PaymentResponse;
import com.moviebooking.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps entities to DTOs. Must be called inside a transaction (touches lazy associations). */
@Component
public class BookingMapper {

    private final PaymentRepository paymentRepository;

    public BookingMapper(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public BookingResponse toResponse(Booking b) {
        Show show = b.getShow();
        List<BookingSeatResponse> seats = b.getItems().stream()
                .map(i -> new BookingSeatResponse(i.getSeatLabel(), i.getSeatType(), i.getPrice()))
                .toList();
        List<PaymentResponse> payments = paymentRepository.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .map(p -> new PaymentResponse(p.getId(), p.getPaymentType(), p.getStatus(), p.getAmount(),
                        p.getPaymentMethod(), p.getPaymentReference(), p.getFailureReason(), p.getCreatedAt()))
                .toList();
        return new BookingResponse(b.getId(), b.getBookingReference(), b.getStatus(), show.getId(),
                show.getMovie().getTitle(), show.getScreen().getTheater().getName(), show.getScreen().getName(),
                show.getStartTime(), seats, b.getSubtotal(), b.getDiscountCode(), b.getDiscountAmount(),
                b.getTotalAmount(), b.getHoldExpiresAt(), b.getCreatedAt(), b.getConfirmedAt(), b.getCancelledAt(),
                b.getRefundAmount(), b.getCancellationReason(), payments);
    }
}
