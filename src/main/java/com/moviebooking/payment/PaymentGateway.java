package com.moviebooking.payment;

import com.moviebooking.domain.PaymentMethod;

import java.math.BigDecimal;

/** Port for the payment provider. Swap the mock for a real integration without touching booking logic. */
public interface PaymentGateway {

    GatewayResult charge(BigDecimal amount, PaymentMethod method, String paymentToken);

    GatewayResult refund(BigDecimal amount, String originalPaymentReference);
}
