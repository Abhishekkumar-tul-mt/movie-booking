package com.moviebooking.service;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Finds overdue holds and expires each one in its own transaction (one bad row cannot block the rest). */
@Service
public class HoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Clock clock;

    public HoldExpiryService(BookingRepository bookingRepository, BookingService bookingService, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    /** @return number of holds released */
    public int sweep() {
        List<Long> ids = bookingRepository.findExpiredIds(BookingStatus.PENDING_PAYMENT, LocalDateTime.now(clock));
        int released = 0;
        for (Long id : ids) {
            try {
                if (bookingService.expireBooking(id)) {
                    released++;
                }
            } catch (Exception ex) {
                log.warn("Failed to expire booking {}: {}", id, ex.getMessage());
            }
        }
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
        return released;
    }
}
