package com.moviebooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Simulated channel: writes the "email" to the log. Replace with a JavaMail / SES implementation. */
@Component
public class LoggingNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationChannel.class);

    @Override
    public void send(String recipient, String subject, String message) {
        log.info("[NOTIFY] to={} subject=\"{}\" body=\"{}\"", recipient, subject, message);
    }
}
