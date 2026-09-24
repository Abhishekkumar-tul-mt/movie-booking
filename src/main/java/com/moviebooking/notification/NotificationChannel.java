package com.moviebooking.notification;

/** Delivery channel abstraction (email / SMS / push). */
public interface NotificationChannel {
    void send(String recipient, String subject, String message) throws Exception;
}
