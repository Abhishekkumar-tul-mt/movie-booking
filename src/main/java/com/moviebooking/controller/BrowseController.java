package com.moviebooking.controller;

import com.moviebooking.dto.CatalogDtos.CityResponse;
import com.moviebooking.dto.CatalogDtos.MovieResponse;
import com.moviebooking.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.dto.ShowDtos.SeatMapResponse;
import com.moviebooking.dto.ShowDtos.ShowResponse;
import com.moviebooking.service.CatalogService;
import com.moviebooking.service.ShowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Read-only browsing endpoints, open to any authenticated user (customer or admin). */
@RestController
@RequestMapping("/api")
public class BrowseController {

    private final CatalogService catalogService;
    private final ShowService showService;

    public BrowseController(CatalogService catalogService, ShowService showService) {
        this.catalogService = catalogService;
        this.showService = showService;
    }

    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return catalogService.listCities();
    }

    @GetMapping("/cities/{cityId}/theaters")
    public List<TheaterResponse> theaters(@PathVariable Long cityId) {
        return catalogService.listTheatersByCity(cityId);
    }

    @GetMapping("/movies")
    public List<MovieResponse> movies() {
        return catalogService.listActiveMovies();
    }

    /** Upcoming scheduled shows, optionally filtered by city / movie / theater / calendar date. */
    @GetMapping("/shows")
    public List<ShowResponse> shows(@RequestParam(required = false) Long cityId,
                                    @RequestParam(required = false) Long movieId,
                                    @RequestParam(required = false) Long theaterId,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showService.search(cityId, movieId, theaterId, date);
    }

    @GetMapping("/shows/{showId}")
    public ShowResponse show(@PathVariable Long showId) {
        return showService.getShow(showId);
    }

    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse seats(@PathVariable Long showId) {
        return showService.seatMap(showId);
    }
}
