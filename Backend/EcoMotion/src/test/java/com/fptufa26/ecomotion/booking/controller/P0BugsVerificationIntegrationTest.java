package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.seed.DevDataSeeder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class P0BugsVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String DEFAULT_PASSWORD = "EcoMotion@123";

    private String loginAs(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    @DisplayName("Test Case 1: Renter loads accepted booking BK-4C4M93 detail successfully (no 404 snapshot error)")
    void testCase1_renterLoadsAcceptedBookingBK4C4M93DetailSuccessfully() throws Exception {
        String token = loginAs("duc.tran@ecomotion.vn");

        String body = mockMvc.perform(get("/api/renter/bookings/BK-4C4M93")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.get("bookingCode").asText()).isEqualTo("BK-4C4M93");
        assertThat(data.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(1400000.0);
        assertThat(data.has("charges")).isTrue();
        assertThat(data.get("canPay").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Test Case 2: Booking eligible for payment generates VNPay Sandbox URL successfully")
    void testCase2_bookingEligibleForPaymentGeneratesVnpaySandboxUrl() throws Exception {
        String token = loginAs("duc.tran@ecomotion.vn");

        String body = mockMvc.perform(post("/api/v1/payment/vnpay/create-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingCode": "BK-4C4M93",
                                  "paymentType": "DEPOSIT",
                                  "language": "vn"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.has("paymentUrl")).isTrue();
        String paymentUrl = data.get("paymentUrl").asText();
        assertThat(paymentUrl).contains("sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        assertThat(paymentUrl).contains("vnp_Amount=42000000"); // 30% of 1,400,000 is 420,000 VND (*100 for vnpay)
    }

    @Test
    @DisplayName("Test Case 3: Driver uploads evidence to Trip 17 successfully")
    void testCase3_driverUploadsEvidenceToTrip17Successfully() throws Exception {
        String driverToken = loginAs("driver.nam@ecomotion.vn");

        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "pickup_arrival.png",
                "image/png",
                new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00 }
        );

        String body = mockMvc.perform(multipart("/api/v1/driver/trips/17/evidence/upload")
                        .file(imageFile)
                        .param("type", "PICKUP_ARRIVAL")
                        .param("note", "Tài xế đã có mặt tại điểm đón đúng giờ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.has("imageUrl")).isTrue();
        assertThat(data.get("imageUrl").asText()).isNotBlank();
        assertThat(data.get("type").asText()).isEqualTo("PICKUP_ARRIVAL");
    }

    @Test
    @DisplayName("Test Case 4: Provider views uploaded evidence for Trip 17")
    void testCase4_providerViewsUploadedEvidence() throws Exception {
        String providerToken = loginAs("provider.a@ecomotion.vn");

        mockMvc.perform(get("/api/provider/bookings/17/evidence")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Test Case 5: Invalid file type returns 400 Bad Request, not 500")
    void testCase5_invalidFileTypeReturns400Not500() throws Exception {
        String driverToken = loginAs("driver.nam@ecomotion.vn");

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "echo hello".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/driver/trips/17/evidence/upload")
                        .file(textFile)
                        .param("type", "PICKUP_ARRIVAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").exists());
    }

    @Test
    @DisplayName("Test Case 6: Unauthorized Renter accessing another user's booking receives 403 Forbidden, not 500")
    void testCase6_unauthorizedRenterReceives403Not500() throws Exception {
        String otherRenterToken = loginAs("renter01@ecomotion.vn"); // Owner of BK-4C4M93 is user 101 duc.tran

        mockMvc.perform(get("/api/renter/bookings/BK-4C4M93")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherRenterToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Test Case 7: Driver not assigned to Trip is denied upload with 403 Forbidden")
    void testCase7_driverNotAssignedDeniedUploadWith403() throws Exception {
        String unassignedDriverToken = loginAs("driver.cuong@ecomotion.vn"); // Trip 17 is assigned to driver.nam

        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "arrival.png",
                "image/png",
                new byte[]{ 1, 2, 3 }
        );

        mockMvc.perform(multipart("/api/v1/driver/trips/17/evidence/upload")
                        .file(imageFile)
                        .param("type", "PICKUP_ARRIVAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unassignedDriverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Test Case 8: Notifications SSE stream connects with query param token and GET /me works")
    void testCase8_sseNotificationsStreamAndAuthMe() throws Exception {
        String token = loginAs("duc.tran@ecomotion.vn");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("duc.tran@ecomotion.vn"));

        mockMvc.perform(get("/api/v1/notifications/stream?token=" + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test Case 9: Driver reports live GPS location on active Trip 17 successfully, and receives 409 on finished trip")
    void testCase9_driverGpsLocationReporting() throws Exception {
        String driverToken = loginAs("driver.nam@ecomotion.vn");

        Instant now = Instant.now();
        String locationPayload = """
                {
                  "latitude": 10.776900,
                  "longitude": 106.700900,
                  "accuracyMeters": 4.5,
                  "recordedAt": "%s"
                }
                """.formatted(now.toString());

        // 1. Success on active trip 17
        mockMvc.perform(post("/api/v1/driver/trips/17/locations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(10.7769))
                .andExpect(jsonPath("$.data.longitude").value(106.7009))
                .andExpect(jsonPath("$.data.trackingState").value("LIVE"));

        // 2. Conflict (409) on completed trip BK-6B2N02 (assigned to driver.nam but completed)
        mockMvc.perform(post("/api/v1/driver/trips/BK-6B2N02/locations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
