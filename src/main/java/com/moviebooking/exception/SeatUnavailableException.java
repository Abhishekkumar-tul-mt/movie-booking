package com.moviebooking.exception;

import java.util.List;

/** 409 - one or more requested seats are held or booked by someone else. */
public class SeatUnavailableException extends ConflictException {

    private final List<Long> unavailableShowSeatIds;

    public SeatUnavailableException(List<Long> unavailableShowSeatIds) {
        super("Seats are no longer available: " + unavailableShowSeatIds);
        this.unavailableShowSeatIds = unavailableShowSeatIds;
    }

    public List<Long> getUnavailableShowSeatIds() {
        return unavailableShowSeatIds;
    }
}
