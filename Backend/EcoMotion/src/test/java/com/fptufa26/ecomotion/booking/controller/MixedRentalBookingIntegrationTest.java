package com.fptufa26.ecomotion.booking.controller;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.seed.DevDataSeeder;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")
class MixedRentalBookingIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingChargeRepository bookingChargeRepository;
    @Autowired private VehicleScheduleRepository vehicleScheduleRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ProviderBankAccountRepository providerBankAccountRepository;

    @Test
    @Transactional
    void mixedQuotePersistsOfficialEndAndRejectsStalePricesOrOccupiedRange() throws Exception {
        String token = login();
        JsonNode vehicle = read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse().getContentAsString())
                .get("data").get(0);
        long vehicleId = vehicle.get("id").asLong();
        ensureProviderBankAccount(vehicleId);
        String quoteBody = objectMapper.writeValueAsString(Map.of("vehicleId", vehicleId,
                "pickupTime", "2026-12-01T01:00:00Z", "requestedEndAt", "2026-12-04T03:00:00Z"));
        JsonNode option = read(mockMvc.perform(post("/api/renter/bookings/rental-quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("options").get(0);
        Map<String,Object> body = new HashMap<>();
        body.put("vehicleId", vehicleId); body.put("pickupTime", "2026-12-01T01:00:00Z");
        body.put("plannedEndAt", option.get("plannedEndAt").asText());
        body.put("pricingItems", objectMapper.readValue(option.get("items").toString(), List.class));
        body.put("expectedRentalAmount", option.get("rentalAmount").decimalValue().add(BigDecimal.ONE));
        body.put("pickupAddress", "18 Hoang Hoa Tham, Tan Binh"); body.put("pickupLatitude", 10.801234);
        body.put("pickupLongitude", 106.652345); body.put("passengerCount", 3);
        mockMvc.perform(post("/api/renter/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
        body.put("expectedRentalAmount", option.get("rentalAmount").decimalValue());
        JsonNode created = read(mockMvc.perform(post("/api/renter/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("data");
        var booking = bookingRepository.findByBookingCode(created.get("bookingCode").asText()).orElseThrow();
        mockMvc.perform(post("/api/renter/bookings/rental-quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Bạn đã có đơn thuê trùng")));

        assertThat(booking.getPlannedEndAt()).isEqualTo(Instant.parse(option.get("plannedEndAt").asText()));
        assertThat(booking.getPricingItems()).hasSize(option.get("items").size());
        assertThat(bookingChargeRepository.findAllByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL))
                .singleElement().satisfies(charge -> {
                    assertThat(charge.getQuantity()).isEqualTo(1);
                    assertThat(charge.getAmount()).isEqualByComparingTo(option.get("rentalAmount").decimalValue());
                });
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId())).singleElement()
                .satisfies(schedule -> assertThat(schedule.getEndAt()).isEqualTo(booking.getPlannedEndAt()));
        mockMvc.perform(post("/api/renter/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/renter/bookings/" + booking.getBookingCode())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pricingItems").isNotEmpty())
                .andExpect(jsonPath("$.data.endTime").value(option.get("plannedEndAt").asText()));
        Map<String,Object> edit = new HashMap<>(body);
        edit.put("pickupTime", "2026-12-08T01:00:00Z");
        mockMvc.perform(patch("/api/renter/bookings/" + booking.getBookingCode() + "/request-details")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(edit))).andExpect(status().isOk());
        assertThat(booking.getPlannedEndAt()).isEqualTo(Instant.parse(option.get("plannedEndAt").asText())
                .plus(Duration.ofDays(7)));
        assertThat(booking.getTotalAmount()).isEqualByComparingTo(option.get("rentalAmount").decimalValue());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentMixedBookingsOnlyCreateOneHold() throws Exception {
        String token = login();
        JsonNode vehicle = read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn().getResponse().getContentAsString())
                .get("data").get(0);
        long vehicleId = vehicle.get("id").asLong();
        ensureProviderBankAccount(vehicleId);
        JsonNode option = read(mockMvc.perform(post("/api/renter/bookings/rental-quote")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("vehicleId", vehicleId,
                        "pickupTime", "2026-12-22T01:00:00Z", "requestedEndAt", "2026-12-25T03:00:00Z"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("options").get(0);
        Map<String,Object> fields = new HashMap<>();
        fields.put("vehicleId", vehicleId); fields.put("pickupTime", "2026-12-22T01:00:00Z");
        fields.put("plannedEndAt", option.get("plannedEndAt").asText());
        fields.put("pricingItems", objectMapper.readValue(option.get("items").toString(), List.class));
        fields.put("expectedRentalAmount", option.get("rentalAmount").decimalValue());
        fields.put("pickupAddress", "18 Hoang Hoa Tham, Tan Binh"); fields.put("pickupLatitude", 10.801234);
        fields.put("pickupLongitude", 106.652345); fields.put("passengerCount", 3);
        String body = objectMapper.writeValueAsString(fields);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Integer> book = () -> {
            start.await();
            return mockMvc.perform(post("/api/renter/bookings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        };
        try {
            var first = executor.submit(book); var second = executor.submit(book); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        } finally { executor.shutdownNow(); }
    }

    private void ensureProviderBankAccount(long vehicleId) {
        var provider = vehicleRepository.findById(vehicleId).orElseThrow().getProvider();
        if (providerBankAccountRepository.findByProvider_IdAndStatus(provider.getId(), BankAccountStatus.ACTIVE)
                .isPresent()) {
            return;
        }
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(provider);
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION PROVIDER");
        account.setStatus(BankAccountStatus.ACTIVE);
        providerBankAccountRepository.saveAndFlush(account);
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
