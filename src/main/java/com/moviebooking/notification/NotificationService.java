package com.moviebooking.notification;

import com.moviebooking.domain.DeliveryStatus;
import com.moviebooking.domain.Notification;
import com.moviebooking.dto.BookingDtos.NotificationResponse;
import com.moviebooking.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final NotificationChannel channel;
    private final NotificationRepository repository;

    public NotificationService(NotificationChannel channel, NotificationRepository repository) {
        this.channel = channel;
        this.repository = repository;
    }

    /** Sends with a small retry loop and records the outcome. Never throws - notification problems must not leak. */
    public void deliver(NotificationEvent event) {
        Notification n = new Notification();
        n.setUserId(event.userId());
        n.setRecipientEmail(event.email());
        n.setBookingId(event.bookingId());
        n.setNotificationType(event.type());
        n.setSubject(event.subject());
        n.setMessage(truncate(event.message()));

        int attempts = 0;
        boolean sent = false;
        while (attempts < MAX_ATTEMPTS && !sent) {
            attempts++;
            try {
                channel.send(event.email(), event.subject(), event.message());
                sent = true;
            } catch (Exception ex) {
                log.warn("Notification attempt {}/{} failed for booking {}: {}", attempts, MAX_ATTEMPTS,
                        event.bookingId(), ex.getMessage());
            }
        }
        n.setAttempts(attempts);
        n.setDeliveryStatus(sent ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
        try {
            repository.save(n);
        } catch (Exception ex) {
            log.error("Could not persist notification for booking {}", event.bookingId(), ex);
        }
    }

    public List<NotificationResponse> forUser(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getNotificationType().name(), n.getSubject(),
                        n.getMessage(), n.getDeliveryStatus().name(), n.getBookingId(), n.getCreatedAt()))
                .toList();
    }

    private String truncate(String s) {
        return s != null && s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
