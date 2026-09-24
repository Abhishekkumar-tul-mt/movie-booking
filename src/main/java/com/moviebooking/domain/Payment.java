package com.moviebooking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ledger entry: one row per charge attempt or refund. */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payment_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String paymentReference;

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Payment(Booking booking, PaymentType paymentType, PaymentStatus status, BigDecimal amount,
                   PaymentMethod paymentMethod, String paymentReference, String failureReason,
                   LocalDateTime createdAt) {
        this.booking = booking;
        this.paymentType = paymentType;
        this.status = status;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }
}
