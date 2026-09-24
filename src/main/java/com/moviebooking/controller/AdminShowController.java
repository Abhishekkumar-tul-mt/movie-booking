package com.moviebooking.controller;

import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.ShowDtos.ShowRequest;
import com.moviebooking.dto.ShowDtos.ShowResponse;
import com.moviebooking.dto.ShowDtos.ShowStatsResponse;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.ShowCancellationService;
import com.moviebooking.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** ADMIN only: show scheduling, occupancy stats, show cancellation and booking oversight. */
@RestController
@RequestMapping("/api/admin")
public class AdminShowController {

    private final ShowService showService;
    private final ShowCancellationService showCancellationService;
    private final BookingService bookingService;

    public AdminShowController(ShowService showService, ShowCancellationService showCancellationService,
                               BookingService bookingService) {
        this.showService = showService;
        this.showCancellationService = showCancellationService;
        this.bookingService = bookingService;
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody ShowRequest req) {
        return showService.createShow(req);
    }

    @GetMapping("/shows/{showId}/stats")
    public ShowStatsResponse stats(@PathVariable Long showId) {
        return showService.stats(showId);
    }

    /** Cancels the show and fully refunds every active booking. */
    @PostMapping("/shows/{showId}/cancel")
    public Map<String, Object> cancelShow(@PathVariable Long showId) {
        int cancelled = showCancellationService.cancelShow(showId);
        return Map.of("showId", showId, "status", "CANCELLED", "bookingsCancelled", cancelled);
    }

    @GetMapping("/bookings")
    public List<BookingResponse> bookings(@RequestParam(required = false) Long showId) {
        return bookingService.adminList(showId);
    }
}
