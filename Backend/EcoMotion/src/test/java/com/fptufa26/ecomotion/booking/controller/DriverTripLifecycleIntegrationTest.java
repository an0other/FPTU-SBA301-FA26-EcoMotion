package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class DriverTripLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DriverRepository driverRepository;

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    @Test
    @DisplayName("Complete Trip Execution Lifecycle: Confirm -> Arrive -> Start -> Location Tracking -> Finish -> Provider Tracking")
    void completeTripLifecycleFlow() throws Exception {
        String driverToken = loginAndGetToken("driver.review@ecomotion.vn", "EcoMotion@123");
        String providerToken = loginAndGetToken("provider.a@ecomotion.vn", "EcoMotion@123");
        String renterToken = loginAndGetToken("kieu.vo@ecomotion.vn", "EcoMotion@123");

        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        Driver driverNam = driverRepository.findAll().stream()
                .filter(d -> "SEED-R16-D001".equals(d.getLicenseNo()))
                .findFirst().orElseThrow();

        booking.setDriver(driverNam);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(Instant.now().minusSeconds(1800));
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setActualStartAt(null);
        booking.setActualEndAt(null);
        bookingRepository.saveAndFlush(booking);

        Long tripId = booking.getId();

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/start", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/confirm", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.executionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.driverConfirmedAt").exists());

        mockMvc.perform(get("/api/provider/bookings/{bookingCode}", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_CONFIRMED"))
                .andExpect(jsonPath("$.data.trip.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/confirm", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/arrive", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.executionStatus").value("ARRIVED"))
                .andExpect(jsonPath("$.data.actualArrivedAt").exists());

        mockMvc.perform(get("/api/provider/bookings/{bookingCode}", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ARRIVED"))
                .andExpect(jsonPath("$.data.trip.status").value("ARRIVED"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/arrive", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/start", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.executionStatus").value("AWAITING_START_CONFIRMATION"))
                .andExpect(jsonPath("$.data.status").value("AWAITING_START_CONFIRMATION"));

        mockMvc.perform(post("/api/renter/bookings/{bookingCode}/confirm-start", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.started").value(true))
                .andExpect(jsonPath("$.data.actualStartAt").exists());

        mockMvc.perform(get("/api/provider/bookings/{bookingCode}", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.trip.status").value("IN_PROGRESS"));

        LocationUpdateRequest locationReq = new LocationUpdateRequest(
                10.776889,
                106.700806,
                12.5,
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/locations", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.latitude").value(10.776889))
                .andExpect(jsonPath("$.data.longitude").value(106.700806))
                .andExpect(jsonPath("$.data.trackingState").value("LIVE"));

        mockMvc.perform(get("/api/v1/provider/trips/{bookingCode}/tracking", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookingCode").value("BK-4M1P88"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.executionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.latestLocation.latitude").value(10.776889))
                .andExpect(jsonPath("$.data.latestLocation.longitude").value(106.700806));

        mockMvc.perform(get("/api/v1/provider/trips/{bookingCode}/location", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.latitude").value(10.776889));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/finish", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("AWAITING_END_CONFIRMATION"))
                .andExpect(jsonPath("$.data.actualEndAt").exists());

        mockMvc.perform(post("/api/renter/bookings/{bookingCode}/confirm-end", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"));

        mockMvc.perform(get("/api/provider/bookings/{bookingCode}", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"))
                .andExpect(jsonPath("$.data.trip.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/finish", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"));

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/locations", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("Unauthorized Driver cannot perform actions on other driver's trip (Case 9: 403 Forbidden)")
    void unauthorizedDriverCannotAct() throws Exception {
        String driverCuongToken = loginAndGetToken("driver.cuong@ecomotion.vn", "EcoMotion@123");

        Booking booking = bookingRepository.findByBookingCode("BK-1P9A55").orElseThrow();
        Long tripId = booking.getId();

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/confirm", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverCuongToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid coordinates are rejected with 400 Bad Request")
    void invalidCoordinatesRejected() throws Exception {
        String driverToken = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");
        Booking booking = bookingRepository.findByBookingCode("BK-1P9A55").orElseThrow();
        Long tripId = booking.getId();

        LocationUpdateRequest invalidLatReq = new LocationUpdateRequest(
                95.123456,
                106.700806,
                10.0,
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/locations", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLatReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Case 1: Provider assigns driver sets Booking = DRIVER_ASSIGNED")
    void case1_providerAssignsDriver() throws Exception {
        String providerToken = loginAndGetToken("provider.a@ecomotion.vn", "EcoMotion@123");
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        Long tripId = booking.getId();

        Driver driverNam = driverRepository.findAll().stream()
                .filter(d -> "SEED-R16-D001".equals(d.getLicenseNo()))
                .findFirst().orElseThrow();

        booking.setPickupTime(Instant.now().plusSeconds(10 * 86400));
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(put("/api/v1/provider/bookings/{bookingCode}/driver", booking.getBookingCode())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("driverId", driverNam.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("DRIVER_ASSIGNED"));

        // Verify Database persistence directly
        Booking updatedBooking = bookingRepository.findById(tripId).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.DRIVER_ASSIGNED);
        assertThat(updatedBooking.getDriver().getId()).isEqualTo(driverNam.getId());
    }

    @Test
    @DisplayName("Tài xế báo sự cố trước chuyến và booking chờ tài xế thay thế")
    void driverReportsUnavailableBeforeTrip() throws Exception {
        String driverToken = loginAndGetToken("driver.review@ecomotion.vn", "EcoMotion@123");
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        Driver driver = driverRepository.findAll().stream()
                .filter(d -> "SEED-R16-D001".equals(d.getLicenseNo()))
                .findFirst().orElseThrow();
        booking.setDriver(driver);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(post("/api/driver/trips/{tripId}/report-unavailable", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "Xe cá nhân bị hỏng trên đường đến điểm đón"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionStatus").value("DRIVER_REPLACEMENT_REQUIRED"));

        Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updated.isDriverReplacementRequired()).isTrue();
        assertThat(updated.getDriverIssueReason()).contains("bị hỏng");
        assertThat(updated.getDriverIssueReportedAt()).isNotNull();

        mockMvc.perform(post("/api/driver/trips/{tripId}/confirm", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Case 10: Trip already COMPLETED cannot be STARTED (409 Conflict)")
    void case10_cannotStartCompletedTrip() throws Exception {
        String driverToken = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        Long tripId = booking.getId();

        Driver driverNam = driverRepository.findAll().stream()
                .filter(d -> d.getUser().getEmail().equalsIgnoreCase("driver.nam@ecomotion.vn"))
                .findFirst().orElseThrow();

        booking.setDriver(driverNam);
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(post("/api/v1/driver/trips/{tripId}/start", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }
}
