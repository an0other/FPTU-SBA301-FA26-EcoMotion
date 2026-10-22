package com.fptufa26.ecomotion.booking.controller;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
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
class DriverTripApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Driver Nam can list assigned trips with pagination and response envelope")
    void driverCanListAssignedTrips() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.items[0].tripId").exists())
                .andExpect(jsonPath("$.data.items[0].bookingCode").exists())
                .andExpect(jsonPath("$.data.items[0].status").exists())
                .andExpect(jsonPath("$.data.items[0].vehicle.licensePlate").exists())
                .andExpect(jsonPath("$.data.items[0].customer.fullName").exists())
                .andExpect(jsonPath("$.data.items[0].customer.phone").exists())
                .andExpect(jsonPath("$.data.items[0].rentalType").exists())
                .andExpect(jsonPath("$.data.items[0].rentalDuration").exists());
    }

    @Test
    @DisplayName("Driver can filter trips by status")
    void driverCanFilterByStatus() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("status", "IN_PROGRESS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].bookingCode").value("BK-1P9A55"))
                .andExpect(jsonPath("$.data.items[0].status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("status", "ASSIGNED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("Driver can filter trips by view: ACTIVE, HISTORY, UPCOMING")
    void driverCanFilterByView() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("view", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("view", "UPCOMING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("Driver can retrieve full detail of assigned trip with customer and provider info")
    void driverCanGetTripDetail() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        MvcResult result = mockMvc.perform(get("/api/v1/driver/trips/BK-1P9A55")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookingCode").value("BK-1P9A55"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.vehicle.brand").value("VinFast"))
                .andExpect(jsonPath("$.data.vehicle.model").value("VF 6"))
                .andExpect(jsonPath("$.data.vehicle.licensePlate").value("51A-11111"))
                .andExpect(jsonPath("$.data.customer.fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.customer.phone").isNotEmpty())
                .andExpect(jsonPath("$.data.provider.displayName").value("EcoMotion Depot A"))
                .andExpect(jsonPath("$.data.provider.hotline").value("02873000010"))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(json);
        JsonNode customerNode = root.get("data").get("customer");
        assertThat(customerNode.has("cccdNo")).isFalse();
        assertThat(customerNode.has("email")).isFalse();
    }

    @Test
    @DisplayName("Driver cannot access another driver's trip (enforces the incoming 403 ownership contract)")
    void driverCannotAccessOtherDriverTrip() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips/BK-1X8C44")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/driver/trips/99999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Reassigning trip immediately revokes access from former driver and grants to new driver")
    void tripReassignmentUpdatesAccessImmediately() throws Exception {
        String providerToken = loginAndGetToken("provider.a@ecomotion.vn", "EcoMotion@123");
        String driverNamToken = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");
        String driverCuongToken = loginAndGetToken("driver.cuong@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips/BK-9Q2Z11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverNamToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/driver/trips/BK-9Q2Z11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverCuongToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/provider/bookings/BK-9Q2Z11/driver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("driverId", 2L))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/driver/trips/BK-9Q2Z11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverNamToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/driver/trips/BK-9Q2Z11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverCuongToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingCode").value("BK-9Q2Z11"));
    }

    @Test
    @DisplayName("Non-driver accounts (Renter, Provider) and unauthenticated users receive 401 or 403")
    void roleAndAuthenticationEnforcement() throws Exception {
        mockMvc.perform(get("/api/v1/driver/trips"))
                .andExpect(status().isUnauthorized());

        String renterToken = loginAndGetToken("renter01@ecomotion.vn", "EcoMotion@123");
        mockMvc.perform(get("/api/v1/driver/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isForbidden());

        String providerToken = loginAndGetToken("provider.a@ecomotion.vn", "EcoMotion@123");
        mockMvc.perform(get("/api/v1/driver/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid filter inputs return 400 Bad Request")
    void invalidFilterInputsReturnBadRequest() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("view", "INVALID_VIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("status", "INVALID_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("date", "bad-date")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("size", "150")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/driver/trips")
                        .param("sort", "malicious_field,asc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Alias routes (/api/driver/trips and /api/driver/schedule) work identically")
    void aliasRoutesWorkCorrectly() throws Exception {
        String token = loginAndGetToken("driver.nam@ecomotion.vn", "EcoMotion@123");

        mockMvc.perform(get("/api/driver/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/driver/schedule")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
