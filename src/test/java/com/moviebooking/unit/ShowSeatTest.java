package com.moviebooking.unit;

import com.moviebooking.domain.ShowSeat;
import com.moviebooking.domain.ShowSeatStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShowSeatTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 24, 12, 0);

    @Test
    void availableSeatIsAvailable() {
        assertThat(new ShowSeat().isAvailableAt(now)).isTrue();
    }

    @Test
    void activeHoldBlocksTheSeatButExpiredHoldDoesNot() {
        ShowSeat seat = new ShowSeat();
        seat.hold(null, now.plusMinutes(3));
        assertThat(seat.isAvailableAt(now)).isFalse();
        assertThat(seat.isAvailableAt(now.plusMinutes(3))).isTrue();  // boundary: expired at the deadline
        assertThat(seat.isAvailableAt(now.plusMinutes(4))).isTrue();
    }

    @Test
    void bookedSeatIsNeverAvailableAndReleaseResetsState() {
        ShowSeat seat = new ShowSeat();
        seat.hold(null, now.plusMinutes(3));
        seat.markBooked();
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.BOOKED);
        assertThat(seat.isAvailableAt(now.plusDays(1))).isFalse();

        seat.release();
        assertThat(seat.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
        assertThat(seat.getBooking()).isNull();
        assertThat(seat.getHoldExpiresAt()).isNull();
    }
}
