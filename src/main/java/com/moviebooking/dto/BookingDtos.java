package com.moviebooking.dto;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.PaymentMethod;
import com.moviebooking.domain.PaymentStatus;
import com.moviebooking.domain.PaymentType;
import com.moviebooking.domain.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BookingDtos {
    private BookingDtos() {
    }

    public record HoldRequest(@NotNull Long showId,
                              @NotEmpty List<@NotNull Long> showSeatIds,
                              @Size(max = 32) String discountCode) {
    }

    /** paymentToken is opaque to us; the mock gateway declines tokens starting with "fail". */
    public record PaymentRequest(@NotNull PaymentMethod paymentMethod, @NotBlank String paymentToken) {
    }

    public record CancelRequest(@Size(max = 200) String reason) {
    }

    public record BookingSeatResponse(String seatLabel, SeatType seatType, BigDecimal price) {
    }

    public record PaymentResponse(Long id, PaymentType paymentType, PaymentStatus status, BigDecimal amount,
                                  PaymentMethod paymentMethod, String paymentReference, String failureReason,
                                  LocalDateTime createdAt) {
    }

    public record BookingResponse(Long id, String bookingReference, BookingStatus status, Long showId,
                                  String movieTitle, String theaterName, String screenName,
                                  LocalDateTime showStartTime, List<BookingSeatResponse> seats,
                                  BigDecimal subtotal, String discountCode, BigDecimal discountAmount,
                                  BigDecimal totalAmount, LocalDateTime holdExpiresAt, LocalDateTime createdAt,
                                  LocalDateTime confirmedAt, LocalDateTime cancelledAt, BigDecimal refundAmount,
                                  String cancellationReason, List<PaymentResponse> payments) {
    }

    public record NotificationResponse(Long id, String type, String subject, String message, String status,
                                       Long bookingId, LocalDateTime createdAt) {
    }
}
