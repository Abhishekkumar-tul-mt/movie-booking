package com.moviebooking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String timezone = "Asia/Kolkata";
    private boolean seedDemoData = true;
    private final Admin admin = new Admin();
    private final Hold hold = new Hold();
    private final Booking booking = new Booking();
    private final Reminder reminder = new Reminder();

    @Getter
    @Setter
    public static class Admin {
        private String email = "admin@movie.com";
        private String password = "Admin@123";
    }

    @Getter
    @Setter
    public static class Hold {
        private int durationMinutes = 5;
        private long sweepIntervalMs = 10000;
    }

    @Getter
    @Setter
    public static class Booking {
        private int maxSeatsPerBooking = 6;
    }

    @Getter
    @Setter
    public static class Reminder {
        private int leadMinutes = 120;
        private long scanIntervalMs = 60000;
    }
}
