package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class VietQrHappyPathIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRequestRepository paymentRequestRepository;
    @Autowired ProviderBankAccountRepository bankAccountRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired VehicleRepository vehicleRepository;

    @Test
    void completeRentalFlowUsesVietQrForDepositAndFinalBalance() throws Exception {
        String renter = login("kieu.vo@ecomotion.vn");
        String provider = login("provider.a@ecomotion.vn");
        Booking booking = createAwaitingDepositBooking(renter);
        ensureBankAccount(booking);

        PaymentRequest deposit = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT).orElseThrow();
        submitProof(renter, deposit, "E2E-DEPOSIT-PROOF");
        confirmPayment(provider, deposit, "E2E-DEPOSIT-BANK");
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        call(put("/api/provider/bookings/{code}/driver", booking.getBookingCode())
                .content(json(Map.of("mode", "AUTO"))), provider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ASSIGNED"));

        Booking assigned = bookingRepository.findById(booking.getId()).orElseThrow();
        String driverToken = login(assigned.getDriver().getUser().getEmail());
        call(post("/api/v1/driver/trips/{id}/confirm", booking.getId()), driverToken).andExpect(status().isOk());
        call(post("/api/v1/driver/trips/{id}/arrive", booking.getId()), driverToken).andExpect(status().isOk());
        call(post("/api/v1/driver/trips/{id}/start", booking.getId()), driverToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_START_CONFIRMATION"));
        call(post("/api/renter/bookings/{code}/confirm-start", booking.getBookingCode()), renter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        call(post("/api/v1/driver/trips/{id}/finish", booking.getId()), driverToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_END_CONFIRMATION"));
        call(post("/api/renter/bookings/{code}/confirm-end", booking.getBookingCode()), renter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"));

        call(post("/api/provider/bookings/{code}/charges", booking.getBookingCode())
                .content(json(Map.of(
                        "chargeType", "WAITING",
                        "description", "Phí chờ kiểm thử E2E",
                        "quantity", 1,
                        "unitPrice", 100000,
                        "amount", 100000))), provider)
                .andExpect(status().isOk());
        call(post("/api/provider/bookings/{code}/settlement/finalize", booking.getBookingCode()), provider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_FINAL_PAYMENT"));

        PaymentRequest balance = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE).orElseThrow();
        submitProof(renter, balance, "E2E-FINAL-PROOF");
        confirmPayment(provider, balance, "E2E-FINAL-BANK");

        call(post("/api/renter/bookings/{code}/reviews", booking.getBookingCode())
                .content(json(Map.of("target", "VEHICLE", "rating", 5, "comment", "Luồng E2E hoàn tất"))), renter)
                .andExpect(status().isCreated());
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
    }

    private Booking createAwaitingDepositBooking(String renterToken) throws Exception {
        JsonNode vehicles = objectMapper.readTree(call(get("/api/renter/bookings/vehicles"), renterToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode vehicle = vehicles.get("data").get(0);
        JsonNode dailyPlan = null;
        for (JsonNode plan : vehicle.get("plans")) {
            if ("DAILY".equals(plan.get("planType").asText())) {
                dailyPlan = plan;
                break;
            }
        }
        assertThat(dailyPlan).isNotNull();
        ensureBankAccountForVehicle(vehicle.get("id").asLong());

        String response = call(post("/api/renter/bookings").content(json(Map.of(
                        "vehicleId", vehicle.get("id").asLong(),
                        "planId", dailyPlan.get("id").asLong(),
                        "slotCount", 2,
                        "pickupTime", "2027-12-15T01:00:00Z",
                        "pickupAddress", "18 Hoàng Hoa Thám, Tân Bình",
                        "pickupLatitude", 10.801234,
                        "pickupLongitude", 106.652345,
                        "driverNote", "Kiểm thử luồng VietQR V3.1",
                        "passengerCount", 2))), renterToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andReturn().getResponse().getContentAsString();
        String bookingCode = objectMapper.readTree(response).get("data").get("bookingCode").asText();
        return bookingRepository.findByBookingCode(bookingCode).orElseThrow();
    }

    private void submitProof(String token, PaymentRequest request, String reference) throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile proof = new MockMultipartFile(
                "file", reference + ".png", MediaType.IMAGE_PNG_VALUE, png);
        mockMvc.perform(multipart("/api/v1/payment-requests/{id}/proofs/multipart", request.getId())
                        .file(proof)
                        .param("transferredAmount", request.getAmount().toPlainString())
                        .param("transactionReference", reference)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void confirmPayment(String token, PaymentRequest request, String reference) throws Exception {
        call(post("/api/v1/provider/payment-requests/{id}/confirm", request.getId())
                .content(json(Map.of(
                        "receivedAmount", request.getAmount(),
                        "bankTransactionRef", reference))), token)
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions call(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "EcoMotion@123"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void ensureBankAccount(Booking booking) {
        if (bankAccountRepository.findByProvider_IdAndStatus(
                booking.getVehicle().getProvider().getId(), BankAccountStatus.ACTIVE).isPresent()) return;
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(providerRepository.findById(booking.getVehicle().getProvider().getId()).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION E2E");
        bankAccountRepository.saveAndFlush(account);
    }

    private void ensureBankAccountForVehicle(long vehicleId) {
        var vehicle = vehicleRepository.findById(vehicleId).orElseThrow();
        if (bankAccountRepository.findByProvider_IdAndStatus(
                vehicle.getProvider().getId(), BankAccountStatus.ACTIVE).isPresent()) return;
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(providerRepository.findById(vehicle.getProvider().getId()).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION E2E");
        account.setStatus(BankAccountStatus.ACTIVE);
        bankAccountRepository.saveAndFlush(account);
    }
}
