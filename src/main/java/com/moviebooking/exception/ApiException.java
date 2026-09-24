package com.moviebooking.exception;

import org.springframework.http.HttpStatus;

/** Base class for all business errors that map to a specific HTTP status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
