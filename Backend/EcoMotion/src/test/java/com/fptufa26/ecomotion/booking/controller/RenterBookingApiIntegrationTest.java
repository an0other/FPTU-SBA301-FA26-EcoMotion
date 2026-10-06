package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
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
class RenterBookingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingChargeRepository bookingChargeRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Test
    @DisplayName("renter creates a PENDING booking with snapshot and base rental, and no payment")
    void createPendingBooking() throws Exception {
        String token = login();
        JsonNode vehicles = read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode vehicle = vehicles.get("data").get(0);
        long vehicleId = vehicle.get("id").asLong();
        JsonNode daily = null;
        for (JsonNode plan : vehicle.get("plans")) {
            if ("DAILY".equals(plan.get("planType").asText())) {
                daily = plan;
            }
        }
        assertThat(daily).isNotNull();

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 3,
                  "pickupTime": "2026-12-01T01:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "passengerCount": 3
                }
                """.formatted(vehicleId, daily.get("id").asLong());

        long paymentsBefore = paymentTransactionRepository.count();
        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.bookingCode").exists())
                .andExpect(jsonPath("$.data.baseRental.chargeType").value("BASE_RENTAL"))
                .andExpect(jsonPath("$.data.baseRental.quantity").value(3));

        assertThat(bookingRepository.count()).isPositive();
        assertThat(bookingChargeRepository.findAll())
                .anyMatch(charge -> charge.getChargeType() == ChargeType.BASE_RENTAL);
        assertThat(paymentTransactionRepository.count()).isEqualTo(paymentsBefore);
    }

    @Test
    @DisplayName("driver plan rejects a passenger count that leaves no seat for the driver")
    void passengerCountAboveGuestSeatsConflicts() throws Exception {
        String token = login();
        JsonNode vehicles = read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode vehicle = vehicles.get("data").get(0);
        JsonNode daily = null;
        for (JsonNode plan : vehicle.get("plans")) {
            if ("DAILY".equals(plan.get("planType").asText())) {
                daily = plan;
            }
        }
        assertThat(daily).isNotNull();
        int seatCount = vehicle.get("seatCount").asInt();

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "2035-04-01T01:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "passengerCount": %d
                }
                """.formatted(vehicle.get("id").asLong(), daily.get("id").asLong(), seatCount);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
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

    private JsonNode read(String json) {
        return objectMapper.readTree(json);
    }
}
