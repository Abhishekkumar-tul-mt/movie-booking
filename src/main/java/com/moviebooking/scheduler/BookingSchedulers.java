package com.moviebooking.scheduler;

import com.moviebooking.service.HoldExpiryService;
import com.moviebooking.service.ReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic jobs. Disable with app.scheduling.enabled=false (tests trigger the services directly). */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class BookingSchedulers {

    private final HoldExpiryService holdExpiryService;
    private final ReminderService reminderService;

    public BookingSchedulers(HoldExpiryService holdExpiryService, ReminderService reminderService) {
        this.holdExpiryService = holdExpiryService;
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${app.hold.sweep-interval-ms:10000}")
    public void releaseExpiredHolds() {
        holdExpiryService.sweep();
    }

    @Scheduled(fixedDelayString = "${app.reminder.scan-interval-ms:60000}")
    public void sendReminders() {
        reminderService.sendDueReminders();
    }
}
