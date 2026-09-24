package com.moviebooking.service;

import com.moviebooking.domain.City;
import com.moviebooking.domain.Movie;
import com.moviebooking.domain.Screen;
import com.moviebooking.domain.Seat;
import com.moviebooking.domain.Theater;
import com.moviebooking.dto.CatalogDtos.CityRequest;
import com.moviebooking.dto.CatalogDtos.CityResponse;
import com.moviebooking.dto.CatalogDtos.MovieRequest;
import com.moviebooking.dto.CatalogDtos.MovieResponse;
import com.moviebooking.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.dto.CatalogDtos.SeatRowRequest;
import com.moviebooking.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.exception.BadRequestException;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.CityRepository;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.TheaterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Admin-managed master data: cities, theaters, screens (+ seat layout) and movies. */
@Service
public class CatalogService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;

    public CatalogService(CityRepository cityRepository, TheaterRepository theaterRepository,
                          ScreenRepository screenRepository, SeatRepository seatRepository,
                          MovieRepository movieRepository) {
        this.cityRepository = cityRepository;
        this.theaterRepository = theaterRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
    }

    // ---------------------------------------------------------------- cities

    @Transactional
    public CityResponse createCity(CityRequest req) {
        String name = req.name().trim();
        if (cityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("City already exists: " + name);
        }
        City city = cityRepository.save(new City(name));
        return toResponse(city);
    }

    @Transactional
    public CityResponse updateCity(Long id, CityRequest req) {
        City city = cityRepository.findById(id).orElseThrow(() -> new NotFoundException("City not found: " + id));
        String name = req.name().trim();
        if (!city.getName().equalsIgnoreCase(name) && cityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("City already exists: " + name);
        }
        city.setName(name);
        return toResponse(city);
    }

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toResponse).toList();
    }

    // -------------------------------------------------------------- theaters

    @Transactional
    public TheaterResponse createTheater(TheaterRequest req) {
        City city = cityRepository.findById(req.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + req.cityId()));
        Theater theater = theaterRepository.save(new Theater(req.name().trim(), req.address(), city));
        return toResponse(theater);
    }

    @Transactional
    public TheaterResponse updateTheater(Long id, TheaterRequest req) {
        Theater theater = theaterRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Theater not found: " + id));
        City city = cityRepository.findById(req.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + req.cityId()));
        theater.setName(req.name().trim());
        theater.setAddress(req.address());
        theater.setCity(city);
        return toResponse(theater);
    }

    @Transactional(readOnly = true)
    public List<TheaterResponse> listTheatersByCity(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new NotFoundException("City not found: " + cityId);
        }
        return theaterRepository.findByCityIdOrderByName(cityId).stream().map(this::toResponse).toList();
    }

    // --------------------------------------------------------------- screens

    /** Creates a screen and generates its seats from the row layout (e.g. A x10 REGULAR, F x8 PREMIUM). */
    @Transactional
    public ScreenResponse createScreen(Long theaterId, ScreenRequest req) {
        Theater theater = theaterRepository.findById(theaterId)
                .orElseThrow(() -> new NotFoundException("Theater not found: " + theaterId));
        String name = req.name().trim();
        if (screenRepository.existsByTheaterIdAndNameIgnoreCase(theaterId, name)) {
            throw new ConflictException("Screen already exists in this theater: " + name);
        }
        Set<String> labels = new HashSet<>();
        int total = 0;
        for (SeatRowRequest row : req.rows()) {
            if (!labels.add(row.label().toUpperCase(Locale.ROOT))) {
                throw new BadRequestException("Duplicate row label: " + row.label());
            }
            total += row.seatCount();
        }

        Screen screen = screenRepository.save(new Screen(name, theater, total));
        List<Seat> seats = new ArrayList<>(total);
        for (SeatRowRequest row : req.rows()) {
            String label = row.label().toUpperCase(Locale.ROOT);
            for (int n = 1; n <= row.seatCount(); n++) {
                seats.add(new Seat(screen, label, n, row.seatType()));
            }
        }
        seatRepository.saveAll(seats);
        return toResponse(screen);
    }

    @Transactional(readOnly = true)
    public List<ScreenResponse> listScreens(Long theaterId) {
        if (!theaterRepository.existsById(theaterId)) {
            throw new NotFoundException("Theater not found: " + theaterId);
        }
        return screenRepository.findByTheaterIdOrderByName(theaterId).stream().map(this::toResponse).toList();
    }

    // ---------------------------------------------------------------- movies

    @Transactional
    public MovieResponse createMovie(MovieRequest req) {
        Movie movie = new Movie();
        apply(movie, req);
        return toResponse(movieRepository.save(movie));
    }

    @Transactional
    public MovieResponse updateMovie(Long id, MovieRequest req) {
        Movie movie = movieRepository.findById(id).orElseThrow(() -> new NotFoundException("Movie not found: " + id));
        apply(movie, req);
        return toResponse(movie);
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> listActiveMovies() {
        return movieRepository.findByActiveTrueOrderByTitle().stream().map(this::toResponse).toList();
    }

    // --------------------------------------------------------------- mapping

    private void apply(Movie movie, MovieRequest req) {
        movie.setTitle(req.title().trim());
        movie.setDescription(req.description());
        movie.setDurationMinutes(req.durationMinutes());
        movie.setLanguage(req.language());
        movie.setGenre(req.genre());
        movie.setActive(req.active() == null || req.active());
    }

    private CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getName());
    }

    private TheaterResponse toResponse(Theater t) {
        return new TheaterResponse(t.getId(), t.getName(), t.getAddress(), t.getCity().getId(), t.getCity().getName());
    }

    private ScreenResponse toResponse(Screen s) {
        return new ScreenResponse(s.getId(), s.getName(), s.getTheater().getId(), s.getTotalSeats());
    }

    private MovieResponse toResponse(Movie m) {
        return new MovieResponse(m.getId(), m.getTitle(), m.getDescription(), m.getDurationMinutes(),
                m.getLanguage(), m.getGenre(), m.isActive());
    }
}
