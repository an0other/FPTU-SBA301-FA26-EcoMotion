package com.fptufa26.ecomotion.booking.controller;

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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RenterBookingListApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("renter sees seeded bookings by public code and no sequential id")
    void listsOwnBookingsWithoutId() throws Exception {
        JsonNode data = list(null);
        assertThat(codes(data)).contains(
                DevDataSeeder.ACCEPTED_BOOKING_CODE,
                DevDataSeeder.PENDING_BOOKING_CODE);
        for (JsonNode row : data) {
            assertThat(row.has("id")).isFalse();
            assertThat(row.has("cancelReason")).isFalse();
        }
    }

    @Test
    @DisplayName("status filter keeps the matching seed code")
    void filtersByStatus() throws Exception {
        assertThat(codes(list("ACCEPTED")))
                .contains(DevDataSeeder.ACCEPTED_BOOKING_CODE)
                .doesNotContain(DevDataSeeder.PENDING_BOOKING_CODE);
        assertThat(codes(list("PENDING")))
                .contains(DevDataSeeder.PENDING_BOOKING_CODE)
                .doesNotContain(DevDataSeeder.ACCEPTED_BOOKING_CODE);
    }

    @Test
    @DisplayName("unknown status is rejected")
    void rejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/renter/bookings")
                        .param("status", "NOT_A_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login()))
                .andExpect(status().isBadRequest());
    }

    private JsonNode list(String status) throws Exception {
        var request = get("/api/renter/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login());
        if (status != null) {
            request.param("status", status);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private List<String> codes(JsonNode data) {
        List<String> codes = new ArrayList<>();
        for (JsonNode row : data) {
            codes.add(row.get("bookingCode").asText());
        }
        return codes;
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
