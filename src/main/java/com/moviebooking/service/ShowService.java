package com.moviebooking.service;

import com.moviebooking.domain.BookingStatus;
import com.moviebooking.domain.Movie;
import com.moviebooking.domain.PricingTier;
import com.moviebooking.domain.RefundPolicy;
import com.moviebooking.domain.Screen;
import com.moviebooking.domain.Seat;
import com.moviebooking.domain.Show;
import com.moviebooking.domain.ShowSeat;
import com.moviebooking.domain.ShowSeatStatus;
import com.moviebooking.domain.ShowStatus;
import com.moviebooking.dto.ShowDtos.SeatMapResponse;
import com.moviebooking.dto.ShowDtos.ShowRequest;
import com.moviebooking.dto.ShowDtos.ShowResponse;
import com.moviebooking.dto.ShowDtos.ShowSeatResponse;
import com.moviebooking.dto.ShowDtos.ShowStatsResponse;
import com.moviebooking.exception.BadRequestException;
import com.moviebooking.exception.ConflictException;
import com.moviebooking.exception.NotFoundException;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.MovieRepository;
import com.moviebooking.repository.PricingTierRepository;
import com.moviebooking.repository.RefundPolicyRepository;
import com.moviebooking.repository.ScreenRepository;
import com.moviebooking.repository.SeatRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShowService {

    /** Gap kept between two shows on the same screen (cleaning / changeover). */
    static final int CHANGEOVER_BUFFER_MINUTES = 15;

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final PricingTierRepository pricingTierRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final BookingRepository bookingRepository;
    private final PricingService pricingService;
    private final Clock clock;

    public ShowService(ShowRepository showRepository, ShowSeatRepository showSeatRepository,
                       MovieRepository movieRepository, ScreenRepository screenRepository,
                       SeatRepository seatRepository, PricingTierRepository pricingTierRepository,
                       RefundPolicyRepository refundPolicyRepository, BookingRepository bookingRepository,
                       PricingService pricingService, Clock clock) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.pricingTierRepository = pricingTierRepository;
        this.refundPolicyRepository = refundPolicyRepository;
        this.bookingRepository = bookingRepository;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    /** Schedules a show and materialises one priced ShowSeat per physical seat of the screen. */
    @Transactional
    public ShowResponse createShow(ShowRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        Movie movie = movieRepository.findById(req.movieId())
                .orElseThrow(() -> new NotFoundException("Movie not found: " + req.movieId()));
        if (!movie.isActive()) {
            throw new BadRequestException("Movie is not active");
        }
        Screen screen = screenRepository.findById(req.screenId())
                .orElseThrow(() -> new NotFoundException("Screen not found: " + req.screenId()));
        PricingTier tier = pricingTierRepository.findById(req.pricingTierId())
                .orElseThrow(() -> new NotFoundException("Pricing tier not found: " + req.pricingTierId()));
        RefundPolicy policy = null;
        if (req.refundPolicyId() != null) {
            policy = refundPolicyRepository.findById(req.refundPolicyId())
                    .orElseThrow(() -> new NotFoundException("Refund policy not found: " + req.refundPolicyId()));
        }

        LocalDateTime start = req.startTime();
        if (!start.isAfter(now)) {
            throw new BadRequestException("Show start time must be in the future");
        }
        LocalDateTime end = start.plusMinutes(movie.getDurationMinutes());
        long overlaps = showRepository.countOverlapping(screen.getId(), ShowStatus.SCHEDULED,
                start.minusMinutes(CHANGEOVER_BUFFER_MINUTES), end.plusMinutes(CHANGEOVER_BUFFER_MINUTES));
        if (overlaps > 0) {
            throw new ConflictException("Screen already has a show overlapping this time slot");
        }

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setPricingTier(tier);
        show.setRefundPolicy(policy);
        show.setStartTime(start);
        show.setEndTime(end);
        show.setStatus(ShowStatus.SCHEDULED);
        showRepository.save(show);

        List<Seat> seats = seatRepository.findByScreenIdOrderByRowLabelAscSeatNumberAsc(screen.getId());
        if (seats.isEmpty()) {
            throw new BadRequestException("Screen has no seats");
        }
        List<ShowSeat> showSeats = new ArrayList<>(seats.size());
        for (Seat seat : seats) {
            showSeats.add(new ShowSeat(show, seat, pricingService.priceFor(seat.getSeatType(), tier, start)));
        }
        showSeatRepository.saveAll(showSeats);
        return toResponse(show);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        return toResponse(findShow(id));
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> search(Long cityId, Long movieId, Long theaterId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        Specification<Show> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), ShowStatus.SCHEDULED));
            p.add(cb.greaterThan(root.<LocalDateTime>get("startTime"), now));
            if (cityId != null) {
                p.add(cb.equal(root.get("screen").get("theater").get("city").get("id"), cityId));
            }
            if (theaterId != null) {
                p.add(cb.equal(root.get("screen").get("theater").get("id"), theaterId));
            }
            if (movieId != null) {
                p.add(cb.equal(root.get("movie").get("id"), movieId));
            }
            if (date != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startTime"), date.atStartOfDay()));
                p.add(cb.lessThan(root.<LocalDateTime>get("startTime"), date.plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return showRepository.findAll(spec, Sort.by("startTime")).stream().map(this::toResponse).toList();
    }

    /** Seat map with lazily-expired holds reported as AVAILABLE. */
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long showId) {
        findShow(showId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ShowSeat> all = showSeatRepository.findByShowIdWithSeat(showId);
        long available = 0;
        List<ShowSeatResponse> seats = new ArrayList<>(all.size());
        for (ShowSeat ss : all) {
            ShowSeatStatus status = ss.isAvailableAt(now) ? ShowSeatStatus.AVAILABLE : ss.getStatus();
            if (status == ShowSeatStatus.AVAILABLE) {
                available++;
            }
            Seat s = ss.getSeat();
            seats.add(new ShowSeatResponse(ss.getId(), s.label(), s.getRowLabel(), s.getSeatNumber(),
                    s.getSeatType(), ss.getPrice(), status));
        }
        return new SeatMapResponse(showId, all.size(), available, seats);
    }

    @Transactional(readOnly = true)
    public ShowStatsResponse stats(Long showId) {
        findShow(showId);
        LocalDateTime now = LocalDateTime.now(clock);
        List<ShowSeat> all = showSeatRepository.findByShowIdWithSeat(showId);
        long available = 0;
        long held = 0;
        long booked = 0;
        for (ShowSeat ss : all) {
            if (ss.isAvailableAt(now)) {
                available++;
            } else if (ss.getStatus() == ShowSeatStatus.HELD) {
                held++;
            } else {
                booked++;
            }
        }
        BigDecimal revenue = bookingRepository.sumTotalByShowAndStatus(showId, BookingStatus.CONFIRMED);
        return new ShowStatsResponse(showId, all.size(), available, held, booked,
                revenue == null ? BigDecimal.ZERO : revenue);
    }

    /** Step 1 of show cancellation: flips the status so no new holds are accepted. */
    @Transactional
    public void markCancelled(Long showId) {
        Show show = findShow(showId);
        if (show.getStatus() == ShowStatus.CANCELLED) {
            throw new ConflictException("Show is already cancelled");
        }
        show.setStatus(ShowStatus.CANCELLED);
    }

    private Show findShow(Long id) {
        return showRepository.findById(id).orElseThrow(() -> new NotFoundException("Show not found: " + id));
    }

    private ShowResponse toResponse(Show s) {
        var theater = s.getScreen().getTheater();
        var city = theater.getCity();
        long available = showSeatRepository.countByShowIdAndStatus(s.getId(), ShowSeatStatus.AVAILABLE);
        return new ShowResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(), theater.getId(),
                theater.getName(), city.getId(), city.getName(), s.getScreen().getId(), s.getScreen().getName(),
                s.getStartTime(), s.getEndTime(), s.getStatus(), s.getPricingTier().getName(),
                s.getRefundPolicy() == null ? null : s.getRefundPolicy().getName(), available);
    }
}
