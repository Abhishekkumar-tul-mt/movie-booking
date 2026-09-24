package com.moviebooking.dto;

import com.moviebooking.domain.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CityRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record CityResponse(Long id, String name) {
    }

    public record TheaterRequest(@NotNull Long cityId, @NotBlank @Size(max = 150) String name,
                                 @Size(max = 300) String address) {
    }

    public record TheaterResponse(Long id, String name, String address, Long cityId, String cityName) {
    }

    /** One row of the seat layout, e.g. label "A", 10 seats, REGULAR. */
    public record SeatRowRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{1,2}$", message = "row label must be 1-2 letters") String label,
            @Min(1) @Max(50) int seatCount,
            @NotNull SeatType seatType) {
    }

    public record ScreenRequest(@NotBlank @Size(max = 100) String name,
                                @NotEmpty @Valid List<SeatRowRequest> rows) {
    }

    public record ScreenResponse(Long id, String name, Long theaterId, int totalSeats) {
    }

    public record MovieRequest(@NotBlank @Size(max = 200) String title,
                               @Size(max = 1000) String description,
                               @Min(1) @Max(600) int durationMinutes,
                               @Size(max = 50) String language,
                               @Size(max = 50) String genre,
                               Boolean active) {
    }

    public record MovieResponse(Long id, String title, String description, int durationMinutes, String language,
                                String genre, boolean active) {
    }
}
