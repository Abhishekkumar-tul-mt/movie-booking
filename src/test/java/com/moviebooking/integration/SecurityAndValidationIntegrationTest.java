package com.moviebooking.integration;

import com.moviebooking.dto.AuthDtos.RegisterRequest;
import com.moviebooking.dto.AuthDtos.UserResponse;
import com.moviebooking.dto.BookingDtos.BookingResponse;
import com.moviebooking.dto.BookingDtos.BookingSeatResponse;
import com.moviebooking.dto.BookingDtos.HoldRequest;
import com.moviebooking.dto.CatalogDtos.CityRequest;
import com.moviebooking.dto.CatalogDtos.SeatRowRequest;
import com.moviebooking.dto.ShowDtos.ShowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAndValidationIntegrationTest extends IntegrationTestBase {

    @Test
    void registrationCreatesCustomerAndRejectsDuplicatesAndBadInput() throws Exception {
        String email = "new-" + uniq() + "@test.com";
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new RegisterRequest("Neo", email, "Sup3rSecret!"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new RegisterRequest("Neo", email, "Sup3rSecret!"))))
                .andExpect(status().isConflict());
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(new RegisterRequest("", "not-an-email", "short"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());

        // the new customer can log in (HTTP Basic)
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").with(httpBasic(email, "Sup3rSecret!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void unauthenticatedAndBadCredentialsGet401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/cities")).andExpect(status().isUnauthorized());
        UserResponse c = createCustomer();
        mvc.perform(MockMvcRequestBuilders.get("/api/cities").with(httpBasic(c.email(), "wrong-password")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void customersCannotUseAdminEndpoints() throws Exception {
        UserResponse c = createCustomer();
        post("/api/admin/cities", c.email(), new CityRequest("Hackville")).andExpect(status().isForbidden());
        get("/api/admin/bookings", c.email()).andExpect(status().isForbidden());
    }

    @Test
    void adminsCannotUseCustomerBookingEndpoints() throws Exception {
        UserResponse admin = createAdmin();
        Fixture f = createShowFixture(hoursFromNow(72));
        post("/api/bookings", admin.email(), new HoldRequest(f.showId(), List.of(f.regularSeatIds().get(0)), null))
                .andExpect(status().isForbidden());
        get("/api/bookings", admin.email()).andExpect(status().isForbidden());
    }

    @Test
    void bothRolesCanBrowse() throws Exception {
        UserResponse admin = createAdmin();
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));
        for (String email : List.of(admin.email(), c.email())) {
            get("/api/cities", email).andExpect(status().isOk());
            get("/api/movies", email).andExpect(status().isOk());
            get("/api/shows", email).andExpect(status().isOk());
            get("/api/shows/" + f.showId() + "/seats", email).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSeats").value(12));
        }
    }

    @Test
    void adminCanManageCatalogAndValidationErrorsAreReported() throws Exception {
        UserResponse admin = createAdmin();
        String cityName = "City-" + uniq();
        post("/api/admin/cities", admin.email(), new CityRequest(cityName)).andExpect(status().isCreated());
        post("/api/admin/cities", admin.email(), new CityRequest(cityName.toUpperCase())).andExpect(status().isConflict());
        post("/api/admin/cities", admin.email(), new CityRequest(" ")).andExpect(status().isBadRequest());
        // invalid seat row
        post("/api/admin/theaters/999999/screens", admin.email(),
                new com.moviebooking.dto.CatalogDtos.ScreenRequest("S", List.of(
                        new SeatRowRequest("1", 5, com.moviebooking.domain.SeatType.REGULAR))))
                .andExpect(status().isBadRequest());
        post("/api/admin/theaters/999999/screens", admin.email(),
                new com.moviebooking.dto.CatalogDtos.ScreenRequest("S", List.of(
                        new SeatRowRequest("A", 5, com.moviebooking.domain.SeatType.REGULAR))))
                .andExpect(status().isNotFound());
    }

    @Test
    void showSchedulingRejectsOverlapsPastTimesAndUnknownReferences() throws Exception {
        UserResponse admin = createAdmin();
        Fixture f = createShowFixture(hoursFromNow(72));
        var existing = showService.getShow(f.showId());

        // same screen, overlapping time -> 409
        post("/api/admin/shows", admin.email(), new ShowRequest(existing.movieId(), existing.screenId(),
                findTierId(admin), null, existing.startTime().plusMinutes(30))).andExpect(status().isConflict());
        // in the past -> 400
        post("/api/admin/shows", admin.email(), new ShowRequest(existing.movieId(), existing.screenId(),
                findTierId(admin), null, hoursFromNow(-2))).andExpect(status().isBadRequest());
        // unknown movie -> 404
        post("/api/admin/shows", admin.email(), new ShowRequest(999999L, existing.screenId(),
                findTierId(admin), null, hoursFromNow(200))).andExpect(status().isNotFound());
    }

    private Long findTierId(UserResponse admin) throws Exception {
        String json = get("/api/admin/pricing-tiers", admin.email()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get(0).get("id").asLong();
    }

    @Test
    void weekendShowsUseTheWeekendMultiplier() throws Exception {
        java.time.LocalDateTime saturday = java.time.LocalDateTime.now(clock)
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY))
                .withHour(19).withMinute(0).withSecond(0).withNano(0);
        Fixture f = createShowFixture(saturday, new java.math.BigDecimal("1.25"));
        UserResponse c = createCustomer();
        BookingResponse b = hold(c.email(), f.showId(), List.of(f.regularSeatIds().get(0), f.premiumSeatIds().get(0)), null);
        List<java.math.BigDecimal> prices = b.seats().stream().map(BookingSeatResponse::price).sorted().toList();
        assertThat(prices.get(0)).isEqualByComparingTo("250.00");
        assertThat(prices.get(1)).isEqualByComparingTo("437.50");
    }

    @Test
    void holdRequestValidation() throws Exception {
        UserResponse c = createCustomer();
        Fixture f = createShowFixture(hoursFromNow(72));
        Fixture other = createShowFixture(hoursFromNow(80));

        // empty seat list -> 400
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), List.of(), null)).andExpect(status().isBadRequest());
        // over the per-booking limit (6) -> 400
        List<Long> seven = new ArrayList<>(f.regularSeatIds().subList(0, 7));
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), seven, null)).andExpect(status().isBadRequest());
        // seat from a different show -> 404
        post("/api/bookings", c.email(), new HoldRequest(f.showId(), List.of(other.regularSeatIds().get(0)), null))
                .andExpect(status().isNotFound());
        // unknown show -> 404
        post("/api/bookings", c.email(), new HoldRequest(999999L, List.of(1L), null)).andExpect(status().isNotFound());
        // nothing was held by any of the failed attempts
        assertThat(showService.seatMap(f.showId()).availableSeats()).isEqualTo(12);
    }
}
