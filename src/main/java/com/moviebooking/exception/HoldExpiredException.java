package com.moviebooking.exception;

import org.springframework.http.HttpStatus;

/** 410 - the seat hold ran out before payment completed. */
public class HoldExpiredException extends ApiException {
    public HoldExpiredException(String message) {
        super(HttpStatus.GONE, message);
    }
}
