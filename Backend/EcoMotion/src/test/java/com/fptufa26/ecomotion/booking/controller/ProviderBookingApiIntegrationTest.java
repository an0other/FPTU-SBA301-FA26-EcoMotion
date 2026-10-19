package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.springframework.transaction.annotation.Transactional
class ProviderBookingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProviderBookingService providerBookingService;

    @Autowired
    private com.fptufa26.ecomotion.user.repository.ProviderRepository providerRepository;

    @Autowired
    private com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository bankAccountRepository;

    private String getProviderToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "provider.a@ecomotion.vn",
                                "password", "EcoMotion@123"))))
                .andExpect(status().isOk())
                .andReturn();

        String json = loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    @Test
    @DisplayName("Provider A has 100% DB bookings loaded across all statuses")
    void providerBookingsAreSeededAndAccessible() throws Exception {
        String token = getProviderToken();

        mockMvc.perform(get("/api/provider/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(31))
                .andExpect(jsonPath("$.data[0].bookingCode").exists())
                .andExpect(jsonPath("$.data[0].renterName").exists())
                .andExpect(jsonPath("$.data[0].vehicleName").exists())
                .andExpect(jsonPath("$.data[0].rentalType").exists())
                .andExpect(jsonPath("$.data[0].rentalDuration").exists())
                .andExpect(jsonPath("$.data[0].totalAmount").exists());

        mockMvc.perform(get("/api/provider/bookings")
                        .param("status", "ALL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(31));

        mockMvc.perform(get("/api/provider/bookings")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/provider/bookings")
                        .param("status", "IN_PROGRESS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].driverName").isNotEmpty());
    }

    @Test
    @DisplayName("Summary endpoint returns live status counts")
    void summaryEndpointReturnsCorrectCounters() throws Exception {
        String token = getProviderToken();

        mockMvc.perform(get("/api/provider/bookings/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(31))
                .andExpect(jsonPath("$.data.pending").value(4))
                .andExpect(jsonPath("$.data.accepted").value(5))
                .andExpect(jsonPath("$.data.confirmed").value(5))
                .andExpect(jsonPath("$.data.inProgress").value(3))
                .andExpect(jsonPath("$.data.completed").value(11));
    }

    @Test
    @DisplayName("Booking detail endpoint returns complete data with relations")
    void bookingDetailEndpointReturnsFullData() throws Exception {
        String token = getProviderToken();

        mockMvc.perform(get("/api/provider/bookings/BK-8F3K29")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.bookingCode").value("BK-8F3K29"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.renterName").isNotEmpty())
                .andExpect(jsonPath("$.data.renterPhone").isNotEmpty())
                .andExpect(jsonPath("$.data.vehicleName").isNotEmpty())
                .andExpect(jsonPath("$.data.licensePlate").isNotEmpty())
                .andExpect(jsonPath("$.data.rentalType").isNotEmpty())
                .andExpect(jsonPath("$.data.pickupAddress").isNotEmpty())
                .andExpect(jsonPath("$.data.timeline.length()").isNumber());
    }

    @Test
    @DisplayName("Accept booking transitions PENDING to ACCEPTED")
    void acceptBookingFlow() throws Exception {
        // The incoming payment workflow requires an active destination account.
        var account = new com.fptufa26.ecomotion.finance.entity.ProviderBankAccount();
        account.setProvider(providerRepository.findByUser_Id(2L).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("NGUYEN VAN A");
        bankAccountRepository.saveAndFlush(account);
        String token = getProviderToken();

        mockMvc.perform(post("/api/provider/bookings/BK-7N2W90/accept")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/provider/bookings/BK-7N2W90")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.providerConfirmedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Deny booking transitions PENDING to REJECTED")
    void denyBookingFlow() throws Exception {
        String token = getProviderToken();

        mockMvc.perform(post("/api/provider/bookings/BK-3D9F12/deny")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Hết xe vào khung giờ này"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/provider/bookings/BK-3D9F12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Assign driver and available drivers endpoints work")
    void driverAssignmentFlow() throws Exception {
        String token = getProviderToken();

        MvcResult driversResult = mockMvc.perform(get("/api/provider/drivers/available")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].fullName").isNotEmpty())
                .andReturn();

        String driversJson = driversResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        long driverId = objectMapper.readTree(driversJson).get("data").get(0).get("id").asLong();

        mockMvc.perform(put("/api/provider/bookings/BK-4M1P88/driver")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("driverId", driverId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.driverName").isNotEmpty());
    }

    @Test
    @DisplayName("Add manual charge updates charges list and total amount")
    void manualChargeFlow() throws Exception {
        String token = getProviderToken();

        Map<String, Object> chargePayload = Map.of(
                "chargeType", ChargeType.WAITING.name(),
                "description", "Chờ khách quá giờ tại sân bay 30 phút",
                "quantity", 30,
                "unitPrice", new BigDecimal("5000.00"),
                "amount", new BigDecimal("150000.00")
        );

        mockMvc.perform(post("/api/provider/bookings/BK-4A2K91/charges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.chargeType").value("WAITING"))
                .andExpect(jsonPath("$.data.amount").value(150000.0));

        mockMvc.perform(get("/api/provider/bookings/BK-4A2K91/charges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("Export bookings CSV returns UTF-8 CSV with data")
    void exportBookingsCsvFlow() throws Exception {
        String token = getProviderToken();

        mockMvc.perform(get("/api/provider/bookings/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv; charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mã đơn,Khách thuê,Xe,Biển số")));
    }

    @Test
    @DisplayName("Direct service query returns enriched provider booking response")
    void serviceReturnsCompleteDto() {
        List<ProviderBookingResponse> bookings = providerBookingService.listBookings(2L, "ALL");
        assertThat(bookings).isNotEmpty();

        ProviderBookingResponse first = bookings.get(0);
        assertThat(first.bookingCode()).isNotBlank();
        assertThat(first.vehicleName()).isNotBlank();
        assertThat(first.licensePlate()).isNotBlank();
        assertThat(first.renterName()).isNotBlank();
        assertThat(first.rentalType()).isNotBlank();
        assertThat(first.rentalDuration()).isNotBlank();
        assertThat(first.totalAmount()).isNotNull();
    }
}
