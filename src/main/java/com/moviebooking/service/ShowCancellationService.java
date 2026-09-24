package com.moviebooking.service;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin cancels a show: (1) stop new holds, (2) cancel + fully refund every active booking, one transaction
 * each. Deliberately not @Transactional itself so a failure on one booking does not roll back the others.
 * Re-running is safe: already-cancelled bookings are skipped.
 */
@Service
public class ShowCancellationService {

    private static final Logger log = LoggerFactory.getLogger(ShowCancellationService.class);

    private final ShowService showService;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    public ShowCancellationService(ShowService showService, BookingService bookingService,
                                   BookingRepository bookingRepository) {
        this.showService = showService;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    /** @return number of bookings cancelled */
    public int cancelShow(Long showId) {
        showService.markCancelled(showId);
        List<Long> ids = bookingRepository.findIdsByShowAndStatuses(showId,
                List.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED));
        int cancelled = 0;
        for (Long id : ids) {
            try {
                bookingService.cancelForShowCancellation(id);
                cancelled++;
            } catch (Exception ex) {
                log.error("Could not cancel booking {} for cancelled show {}", id, showId, ex);
            }
        }
        return cancelled;
    }
}
