package com.moviebooking.config;

import com.moviebooking.domain.DiscountType;
import com.moviebooking.domain.Role;
import com.moviebooking.domain.SeatType;
import com.moviebooking.dto.CatalogDtos.CityRequest;
import com.moviebooking.dto.CatalogDtos.CityResponse;
import com.moviebooking.dto.CatalogDtos.MovieRequest;
import com.moviebooking.dto.CatalogDtos.MovieResponse;
import com.moviebooking.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.dto.CatalogDtos.SeatRowRequest;
import com.moviebooking.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.dto.PricingDtos.DiscountCodeRequest;
import com.moviebooking.dto.PricingDtos.PricingTierRequest;
import com.moviebooking.dto.PricingDtos.PricingTierResponse;
import com.moviebooking.dto.PricingDtos.RefundPolicyRequest;
import com.moviebooking.dto.PricingDtos.RefundRuleDto;
import com.moviebooking.dto.ShowDtos.ShowRequest;
import com.moviebooking.repository.UserRepository;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.CatalogService;
import com.moviebooking.service.DiscountService;
import com.moviebooking.service.PricingService;
import com.moviebooking.service.RefundPolicyService;
import com.moviebooking.service.ShowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Startup bootstrap:
 *  - always: admin account (from config) and a default refund policy
 *  - if app.seed-demo-data=true: a demo customer, a city/theater/screens, movies, tiers, codes and shows
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties props;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefundPolicyService refundPolicyService;
    private final CatalogService catalogService;
    private final PricingService pricingService;
    private final DiscountService discountService;
    private final ShowService showService;
    private final Clock clock;

    public DataSeeder(AppProperties props, UserRepository userRepository, AuthService authService,
                      RefundPolicyService refundPolicyService, CatalogService catalogService,
                      PricingService pricingService, DiscountService discountService, ShowService showService,
                      Clock clock) {
        this.props = props;
        this.userRepository = userRepository;
        this.authService = authService;
        this.refundPolicyService = refundPolicyService;
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.discountService = discountService;
        this.showService = showService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(props.getAdmin().getEmail().toLowerCase())) {
            authService.create("Administrator", props.getAdmin().getEmail(), props.getAdmin().getPassword(), Role.ADMIN);
            log.info("Bootstrapped admin account {}", props.getAdmin().getEmail());
        }
        if (!refundPolicyService.hasDefault()) {
            refundPolicyService.create(new RefundPolicyRequest("Standard", true, List.of(
                    new RefundRuleDto(48, 100), new RefundRuleDto(24, 75),
                    new RefundRuleDto(6, 50), new RefundRuleDto(2, 25))));
        }
        if (props.isSeedDemoData() && catalogService.listCities().isEmpty()) {
            seedDemo();
        }
    }

    private void seedDemo() {
        authService.create("Demo Customer", "customer@movie.com", "Customer@123", Role.CUSTOMER);

        CityResponse blr = catalogService.createCity(new CityRequest("Bengaluru"));
        catalogService.createCity(new CityRequest("Mumbai"));
        TheaterResponse theater = catalogService.createTheater(
                new TheaterRequest(blr.id(), "PVR Orion Mall", "Rajajinagar, Bengaluru"));
        ScreenResponse screen1 = catalogService.createScreen(theater.id(), new ScreenRequest("Screen 1", List.of(
                new SeatRowRequest("A", 10, SeatType.REGULAR), new SeatRowRequest("B", 10, SeatType.REGULAR),
                new SeatRowRequest("C", 10, SeatType.REGULAR), new SeatRowRequest("D", 8, SeatType.PREMIUM),
                new SeatRowRequest("E", 8, SeatType.PREMIUM))));
        ScreenResponse screen2 = catalogService.createScreen(theater.id(), new ScreenRequest("Screen 2", List.of(
                new SeatRowRequest("A", 8, SeatType.REGULAR), new SeatRowRequest("B", 8, SeatType.PREMIUM))));

        MovieResponse m1 = catalogService.createMovie(new MovieRequest("Interstellar", "Sci-fi epic", 150, "English",
                "Sci-Fi", true));
        MovieResponse m2 = catalogService.createMovie(new MovieRequest("Kantara", "Folk thriller", 120, "Kannada",
                "Thriller", true));

        PricingTierResponse tier = pricingService.create(new PricingTierRequest("Standard",
                new BigDecimal("200"), new BigDecimal("350"), new BigDecimal("1.25")));

        LocalDateTime now = LocalDateTime.now(clock);
        discountService.create(new DiscountCodeRequest("WELCOME10", DiscountType.PERCENTAGE, new BigDecimal("10"),
                new BigDecimal("100"), null, now.minusDays(1), now.plusDays(90), null, 3, true));
        discountService.create(new DiscountCodeRequest("FLAT50", DiscountType.FLAT, new BigDecimal("50"), null,
                new BigDecimal("300"), now.minusDays(1), now.plusDays(90), 100, null, true));

        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        LocalDate saturday = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        create(m1.id(), screen1.id(), tier.id(), tomorrow.atTime(10, 0));
        create(m2.id(), screen1.id(), tier.id(), tomorrow.atTime(14, 0));
        create(m1.id(), screen1.id(), tier.id(), tomorrow.atTime(18, 30));
        create(m2.id(), screen2.id(), tier.id(), tomorrow.atTime(20, 0));
        create(m1.id(), screen2.id(), tier.id(), saturday.atTime(12, 0)); // weekend pricing (screen 2 avoids clashes)
        log.info("Seeded demo data. Admin: {} / customer: customer@movie.com", props.getAdmin().getEmail());
    }

    private void create(Long movieId, Long screenId, Long tierId, LocalDateTime start) {
        showService.createShow(new ShowRequest(movieId, screenId, tierId, null, start));
    }
}
