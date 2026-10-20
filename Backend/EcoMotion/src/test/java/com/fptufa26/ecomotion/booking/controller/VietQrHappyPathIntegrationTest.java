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
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @Autowired DriverRepository driverRepository;
    @Autowired PaymentRequestRepository paymentRequestRepository;
    @Autowired ProviderBankAccountRepository bankAccountRepository;
    @Autowired ProviderRepository providerRepository;

    @Test
    void completeRentalFlowUsesVietQrForDepositAndFinalBalance() throws Exception {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        String renter = login(booking.getRenter().getUser().getEmail());
        String provider = login("provider.a@ecomotion.vn");
        String driverToken = login("driver.nam@ecomotion.vn");

        booking.setStatus(BookingStatus.PENDING);
        booking.setDriver(null);
        booking.setAssignedAt(null);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setActualStartAt(null);
        booking.setActualEndAt(null);
        booking.setProviderConfirmedAt(null);
        booking.setRenterConfirmedAt(null);
        bookingRepository.saveAndFlush(booking);
        ensureBankAccount(booking);

        call(post("/api/provider/bookings/{code}/accept", booking.getBookingCode()), provider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        PaymentRequest deposit = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT).orElseThrow();
        submitProof(renter, deposit, "E2E-DEPOSIT-PROOF");
        confirmPayment(provider, deposit, "E2E-DEPOSIT-BANK");
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        Driver driver = driverRepository.findAll().stream()
                .filter(row -> row.getUser().getEmail().equalsIgnoreCase("driver.nam@ecomotion.vn"))
                .findFirst().orElseThrow();
        call(put("/api/provider/bookings/{code}/driver", booking.getBookingCode())
                .content(json(Map.of("driverId", driver.getId()))), provider)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRIVER_ASSIGNED"));

        call(post("/api/v1/driver/trips/{id}/confirm", booking.getId()), driverToken).andExpect(status().isOk());
        call(post("/api/v1/driver/trips/{id}/arrive", booking.getId()), driverToken).andExpect(status().isOk());
        call(post("/api/v1/driver/trips/{id}/start", booking.getId()), driverToken).andExpect(status().isOk());
        call(post("/api/v1/driver/trips/{id}/finish", booking.getId()), driverToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"));
        call(post("/api/renter/bookings/{code}/confirm-end", booking.getBookingCode()), renter)
                .andExpect(status().isOk());

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

    private void submitProof(String token, PaymentRequest request, String reference) throws Exception {
        call(post("/api/v1/payment-requests/{id}/proofs", request.getId())
                .content(json(Map.of(
                        "proofImageUrl", "https://example.com/proof.png",
                        "transferredAmount", request.getAmount(),
                        "transactionReference", reference))), token)
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
}
