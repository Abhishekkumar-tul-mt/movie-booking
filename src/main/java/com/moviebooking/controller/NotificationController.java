package com.moviebooking.controller;

import com.moviebooking.dto.BookingDtos.NotificationResponse;
import com.moviebooking.notification.NotificationService;
import com.moviebooking.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The customer's notification inbox (confirmations, reminders, cancellations, refunds). */
    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal AppUserPrincipal me) {
        return notificationService.forUser(me.getId());
    }
}
