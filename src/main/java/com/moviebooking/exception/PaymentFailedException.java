package com.moviebooking.exception;

import org.springframework.http.HttpStatus;

/** 402 - the payment gateway declined the charge. The hold stays active so the customer can retry. */
public class PaymentFailedException extends ApiException {
    public PaymentFailedException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, message);
    }
}
