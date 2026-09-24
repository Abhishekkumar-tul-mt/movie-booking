package com.moviebooking.controller;

import com.moviebooking.dto.CatalogDtos.CityRequest;
import com.moviebooking.dto.CatalogDtos.CityResponse;
import com.moviebooking.dto.CatalogDtos.MovieRequest;
import com.moviebooking.dto.CatalogDtos.MovieResponse;
import com.moviebooking.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only: cities, theaters, screens (seat layouts) and movies. */
@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest req) {
        return catalogService.createCity(req);
    }

    @PutMapping("/cities/{id}")
    public CityResponse updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest req) {
        return catalogService.updateCity(id, req);
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody TheaterRequest req) {
        return catalogService.createTheater(req);
    }

    @PutMapping("/theaters/{id}")
    public TheaterResponse updateTheater(@PathVariable Long id, @Valid @RequestBody TheaterRequest req) {
        return catalogService.updateTheater(id, req);
    }

    @PostMapping("/theaters/{theaterId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@PathVariable Long theaterId, @Valid @RequestBody ScreenRequest req) {
        return catalogService.createScreen(theaterId, req);
    }

    @GetMapping("/theaters/{theaterId}/screens")
    public List<ScreenResponse> screens(@PathVariable Long theaterId) {
        return catalogService.listScreens(theaterId);
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest req) {
        return catalogService.createMovie(req);
    }

    @PutMapping("/movies/{id}")
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest req) {
        return catalogService.updateMovie(id, req);
    }
}
