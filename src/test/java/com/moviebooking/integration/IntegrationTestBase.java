package com.moviebooking.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.domain.PaymentMethod;
import com.moviebooking.domain.Role;
import com.moviebooking.domain.SeatType;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.BookingDtos.PaymentRequest;
import com.moviebooking.dto.CatalogDtos.CityRequest;
import com.moviebooking.dto.CatalogDtos.CityResponse;
import com.moviebooking.dto.CatalogDtos.MovieRequest;
import com.moviebooking.dto.CatalogDtos.MovieResponse;
import com.moviebooking.dto.CatalogDtos.ScreenRequest;
import com.moviebooking.dto.CatalogDtos.ScreenResponse;
import com.moviebooking.dto.CatalogDtos.SeatRowRequest;
import com.moviebooking.dto.CatalogDtos.TheaterRequest;
import com.moviebooking.dto.CatalogDtos.TheaterResponse;
import com.moviebooking.dto.PricingDtos.PricingTierRequest;
import com.moviebooking.dto.PricingDtos.PricingTierResponse;
import com.moviebooking.dto.PricingDtos.RefundPolicyRequest;
import com.moviebooking.dto.PricingDtos.RefundPolicyResponse;
import com.moviebooking.dto.PricingDtos.RefundRuleDto;
import com.moviebooking.dto.ShowDtos.SeatMapResponse;
import com.moviebooking.dto.ShowDtos.ShowRequest;
import com.moviebooking.dto.ShowDtos.ShowResponse;
import com.moviebooking.dto.ShowDtos.ShowSeatResponse;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.repository.NotificationRepository;
import com.moviebooking.repository.PaymentRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.BookingService;
import com.moviebooking.service.CatalogService;
import com.moviebooking.service.HoldExpiryService;
import com.moviebooking.service.PricingService;
import com.moviebooking.service.RefundPolicyService;
import com.moviebooking.service.ReminderService;
import com.moviebooking.service.ShowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application (H2, real services, real security filter chain) once and shares the context.
 * Every test builds its own uniquely-named data so tests are independent of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Passw0rd!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired protected AuthService authService;
    @Autowired protected CatalogService catalogService;
    @Autowired protected PricingService pricingService;
    @Autowired protected RefundPolicyService refundPolicyService;
    @Autowired protected ShowService showService;
    @Autowired protected BookingService bookingService;
    @Autowired protected HoldExpiryService holdExpiryService;
    @Autowired protected ReminderService reminderService;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected ShowSeatRepository showSeatRepository;
    @Autowired protected PaymentRepository paymentRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected DiscountCodeRepository discountCodeRepository;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Clock clock;

    /** 8 REGULAR seats (A1..A8, 200 each) and 4 PREMIUM seats (B1..B4, 350 each). */
    public record Fixture(Long showId, List<Long> regularSeatIds, List<Long> premiumSeatIds) {
    }

    protected static String uniq() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected LocalDateTime hoursFromNow(long hours) {
        return LocalDateTime.now(clock).plusHours(hours);
    }

    // ------------------------------------------------------------------------------------------ data

    protected UserResponse createCustomer() {
        return authService.create("Customer", "cust-" + uniq() + "@test.com", PASSWORD, Role.CUSTOMER);
    }

    protected UserResponse createAdmin() {
        return authService.create("Admin", "admin-" + uniq() + "@test.com", PASSWORD, Role.ADMIN);
    }

    /** Weekend multiplier 1.00 so prices do not depend on which weekday the test happens to run. */
    protected Fixture createShowFixture(LocalDateTime start) {
        return createShowFixture(start, new BigDecimal("1.00"));
    }

    protected Fixture createShowFixture(LocalDateTime start, BigDecimal weekendMultiplier) {
        String u = uniq();
        CityResponse city = catalogService.createCity(new CityRequest("City-" + u));
        TheaterResponse theater = catalogService.createTheater(new TheaterRequest(city.id(), "Theater-" + u, "Addr"));
        ScreenResponse screen = catalogService.createScreen(theater.id(), new ScreenRequest("Screen-" + u, List.of(
                new SeatRowRequest("A", 8, SeatType.REGULAR), new SeatRowRequest("B", 4, SeatType.PREMIUM))));
        MovieResponse movie = catalogService.createMovie(
                new MovieRequest("Movie-" + u, "desc", 120, "English", "Drama", true));
        PricingTierResponse tier = pricingService.create(new PricingTierRequest("Tier-" + u,
                new BigDecimal("200"), new BigDecimal("350"), weekendMultiplier));
        RefundPolicyResponse policy = refundPolicyService.create(new RefundPolicyRequest("Policy-" + u, false,
                List.of(new RefundRuleDto(48, 100), new RefundRuleDto(24, 75), new RefundRuleDto(6, 50))));
        ShowResponse show = showService.createShow(
                new ShowRequest(movie.id(), screen.id(), tier.id(), policy.id(), start));
        SeatMapResponse map = showService.seatMap(show.id());
        List<Long> regular = map.seats().stream().filter(s -> s.seatType() == SeatType.REGULAR)
                .map(ShowSeatResponse::showSeatId).toList();
        List<Long> premium = map.seats().stream().filter(s -> s.seatType() == SeatType.PREMIUM)
                .map(ShowSeatResponse::showSeatId).toList();
        return new Fixture(show.id(), regular, premium);
    }

    /** Simulates the clock passing the hold deadline without waiting minutes. */
    protected void backdateHold(Long bookingId) {
        Timestamp past = Timestamp.valueOf(LocalDateTime.now(clock).minusMinutes(1));
        jdbc.update("update bookings set hold_expires_at = ? where id = ?", past, bookingId);
        jdbc.update("update show_seats set hold_expires_at = ? where booking_id = ?", past, bookingId);
    }

    // ------------------------------------------------------------------------------------------ HTTP

    protected ResultActions post(String url, String email, Object body) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(url).with(httpBasic(email, PASSWORD));
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        }
        return mvc.perform(b);
    }

    protected ResultActions get(String url, String email) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).with(httpBasic(email, PASSWORD)));
    }

    protected <T> T read(ResultActions result, Class<T> type) throws Exception {
        return om.readValue(result.andReturn().getResponse().getContentAsString(), type);
    }

    protected BookingResponse hold(String email, Long showId, List<Long> seatIds, String code) throws Exception {
        return read(post("/api/bookings", email, new HoldRequest(showId, seatIds, code))
                .andExpect(status().isCreated()), BookingResponse.class);
    }

    protected BookingResponse pay(String email, Long bookingId, String token) throws Exception {
        return read(post("/api/bookings/" + bookingId + "/payment", email,
                new PaymentRequest(PaymentMethod.CARD, token)).andExpect(status().isOk()), BookingResponse.class);
    }

    protected BookingResponse holdAndPay(String email, Long showId, List<Long> seatIds) throws Exception {
        BookingResponse held = hold(email, showId, seatIds, null);
        return pay(email, held.id(), "tok_ok");
    }
}
