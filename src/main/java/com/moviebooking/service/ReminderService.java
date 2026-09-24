package com.moviebooking.service;

import com.moviebooking.config.AppProperties;
import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.ShowStatus;
import com.moviebooking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Queues show reminders for confirmed bookings whose show starts within the configured lead time. */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final AppProperties props;
    private final Clock clock;

    public ReminderService(BookingRepository bookingRepository, BookingService bookingService, AppProperties props,
                           Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.props = props;
        this.clock = clock;
    }

    /** @return number of reminders queued */
    public int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.plusMinutes(props.getReminder().getLeadMinutes());
        List<Long> ids = bookingRepository.findDueForReminder(BookingStatus.CONFIRMED, ShowStatus.SCHEDULED, now, until);
        int sent = 0;
        for (Long id : ids) {
            try {
                if (bookingService.sendReminder(id)) {
                    sent++;
                }
            } catch (Exception ex) {
                log.warn("Failed to queue reminder for booking {}: {}", id, ex.getMessage());
            }
        }
        return sent;
    }
}
