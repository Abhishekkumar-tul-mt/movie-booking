package com.moviebooking.dto;

import com.moviebooking.domain.SeatType;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.domain.ShowStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ShowDtos {
    private ShowDtos() {
    }

    public record ShowRequest(@NotNull Long movieId, @NotNull Long screenId, @NotNull Long pricingTierId,
                              Long refundPolicyId, @NotNull LocalDateTime startTime) {
    }

    public record ShowResponse(Long id, Long movieId, String movieTitle, Long theaterId, String theaterName,
                               Long cityId, String cityName, Long screenId, String screenName,
                               LocalDateTime startTime, LocalDateTime endTime, ShowStatus status,
                               String pricingTierName, String refundPolicyName, long availableSeats) {
    }

    public record ShowSeatResponse(Long showSeatId, String label, String rowLabel, int seatNumber,
                                   SeatType seatType, BigDecimal price, ShowSeatStatus status) {
    }

    public record SeatMapResponse(Long showId, int totalSeats, long availableSeats, List<ShowSeatResponse> seats) {
    }

    public record ShowStatsResponse(Long showId, int totalSeats, long available, long held, long booked,
                                    BigDecimal confirmedRevenue) {
    }
}
