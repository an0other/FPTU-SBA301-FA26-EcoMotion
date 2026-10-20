package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.seed.DevDataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RenterCheckoutApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Test
    @DisplayName("legacy checkout is disabled for accepted bookings")
    void checkoutDepositLocksSchedule() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/renter/bookings/" + DevDataSeeder.ACCEPTED_BOOKING_CODE + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_CHECKOUT_DISABLED"));

        String code = createBooking(token, "DAILY", 3, "2026-12-05T01:00:00Z");
        accept(code);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_CHECKOUT_DISABLED"));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("legacy checkout is disabled before provider acceptance too")
    void checkoutPendingConflicts() throws Exception {
        String token = login();
        String code = createBooking(token, "DAILY", 1, "2026-12-10T01:00:00Z");

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_CHECKOUT_DISABLED"));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("legacy yearly checkout is disabled in favor of VietQR")
    void checkoutYearlyUpfront() throws Exception {
        String token = login();
        String code = createBooking(token, "YEARLY", 1, "2027-06-01T01:00:00Z");
        accept(code);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"E_WALLET\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("LEGACY_CHECKOUT_DISABLED"));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    private String createBooking(String token, String planType, int slotCount, String pickupTime) throws Exception {
        JsonNode vehicles = objectMapper.readTree(mockMvc.perform(get("/api/renter/bookings/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode vehicle = vehicles.get("data").get(0);
        JsonNode plan = null;
        for (JsonNode candidate : vehicle.get("plans")) {
            if (planType.equals(candidate.get("planType").asText())) {
                plan = candidate;
            }
        }
        assertThat(plan).isNotNull();
        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": %d,
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "passengerCount": 2
                }
                """.formatted(vehicle.get("id").asLong(), plan.get("id").asLong(), slotCount, pickupTime);
        String response = mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("bookingCode").asText();
    }

    private void accept(String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(DevDataSeeder.RENTER_EMAIL, DevDataSeeder.DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
