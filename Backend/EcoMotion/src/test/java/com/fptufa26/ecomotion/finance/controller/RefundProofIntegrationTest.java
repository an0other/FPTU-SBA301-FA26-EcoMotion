package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RefundProofIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentTransactionRepository transactionRepository;

    @Test
    void refundOnlyCompletesWithProofAndParticipantsCanReadIt() throws Exception {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        booking.setStatus(BookingStatus.CANCELLATION_REVIEW);
        bookingRepository.saveAndFlush(booking);

        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setType(PaymentType.REFUND);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(PaymentMethod.BANK_TRANSFER);
        refund.setStatus(PaymentStatus.PENDING);
        refund.setAmount(new BigDecimal("100000.00"));
        refund = transactionRepository.saveAndFlush(refund);

        String providerToken = login("provider.a@ecomotion.vn");
        String renterToken = login(booking.getRenter().getUser().getEmail());
        String unrelatedDriverToken = login("driver.nam@ecomotion.vn");
        MockMultipartFile proof = new MockMultipartFile(
                "file", "refund.png", MediaType.IMAGE_PNG_VALUE,
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});

        mockMvc.perform(multipart("/api/v1/payment-transactions/{id}/refund-proof", refund.getId())
                        .file(proof)
                        .param("bankReference", " refund-bank-001 ")
                        .param("note", "Đã hoàn tiền thừa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.bankReference").value("REFUND-BANK-001"));

        PaymentTransaction completed = transactionRepository.findById(refund.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(completed.getReceiptUrl()).isNotBlank();
        assertThat(completed.getPaidAt()).isNotNull();
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        mockMvc.perform(get("/api/v1/payment-transactions/{id}/refund-proof", refund.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + renterToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE));

        mockMvc.perform(get("/api/v1/payment-transactions/{id}/refund-proof", refund.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedDriverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidOrMissingProofCannotCompleteRefund() throws Exception {
        Booking booking = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setType(PaymentType.REFUND);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(PaymentMethod.BANK_TRANSFER);
        refund.setStatus(PaymentStatus.PENDING);
        refund.setAmount(new BigDecimal("50000.00"));
        refund = transactionRepository.saveAndFlush(refund);
        String providerToken = login("provider.a@ecomotion.vn");
        MockMultipartFile invalid = new MockMultipartFile(
                "file", "refund.txt", MediaType.TEXT_PLAIN_VALUE, "not proof".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/payment-transactions/{id}/refund-proof", refund.getId())
                        .file(invalid)
                        .param("bankReference", "REFUND-BANK-INVALID")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken))
                .andExpect(status().isBadRequest());

        assertThat(transactionRepository.findById(refund.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "EcoMotion@123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
