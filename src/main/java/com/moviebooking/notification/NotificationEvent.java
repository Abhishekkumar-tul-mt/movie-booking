package com.moviebooking.notification;

import com.moviebooking.domain.NotificationType;

/** Plain-data event (no entities) so it is safe to hand to another thread. */
public record NotificationEvent(Long userId, String email, Long bookingId, NotificationType type,
                                String subject, String message) {
}
