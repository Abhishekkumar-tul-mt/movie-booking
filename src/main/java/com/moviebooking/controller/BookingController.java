package com.moviebooking.controller;

import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.CancelRequest;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.dto.PricingDtos.RefundQuote;
import com.moviebooking.security.AppUserPrincipal;
import com.moviebooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CUSTOMER only. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Step 1: hold seats (time-boxed). Returns the PENDING_PAYMENT booking with its hold expiry. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@AuthenticationPrincipal AppUserPrincipal me, @Valid @RequestBody HoldRequest req) {
        return bookingService.createHold(me.getId(), req);
    }

    /** Step 2: pay before the hold expires. */
    @PostMapping("/{id}/payment")
    public BookingResponse pay(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                               @Valid @RequestBody PaymentRequest req) {
        return bookingService.pay(me.getId(), id, req);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) CancelRequest req) {
        return bookingService.cancel(me.getId(), id, req == null ? null : req.reason());
    }

    @GetMapping("/{id}/refund-quote")
    public RefundQuote refundQuote(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return bookingService.refundQuote(me.getId(), id);
    }

    /** Booking history, newest first. */
    @GetMapping
    public List<BookingResponse> history(@AuthenticationPrincipal AppUserPrincipal me) {
        return bookingService.myBookings(me.getId());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal AppUserPrincipal me, @PathVariable Long id) {
        return bookingService.getMyBooking(me.getId(), id);
    }
}
