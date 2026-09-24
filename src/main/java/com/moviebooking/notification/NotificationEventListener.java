package com.moviebooking.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs AFTER the booking transaction commits (so we never notify about something that rolled back) and on a
 * separate thread pool (so the HTTP request never waits for delivery).
 * fallbackExecution = true also delivers events published outside a transaction (e.g. by tests / jobs).
 */
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotification(NotificationEvent event) {
        notificationService.deliver(event);
    }
}
