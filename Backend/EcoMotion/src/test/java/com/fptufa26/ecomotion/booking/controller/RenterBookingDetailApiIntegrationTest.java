package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.seed.DevDataSeeder;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.SubmitProofRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentEvidenceRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RenterBookingDetailApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private PaymentEvidenceRepository paymentEvidenceRepository;

    @Autowired
    private ProviderBankAccountRepository bankAccountRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Test
    @DisplayName("request details can be edited before the deposit locks the booking")
    void updateRequestDetailsBeforeDepositLock() throws Exception {
        String token = login();
        String code = createBooking(token, "DAILY", 1, "2027-10-01T01:00:00Z");

        String update = """
                {
                  "pickupTime": "2027-10-02T02:00:00Z",
                  "pickupAddress": "Ben xe Mien Dong Moi",
                  "pickupLatitude": 10.879100,
                  "pickupLongitude": 106.814600,
                  "passengerCount": 3,
                  "driverNote": "Co nguoi lon tuoi\\nCan ho tro"
                }
                """;

        mockMvc.perform(patch("/api/renter/bookings/" + code + "/request-details")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickupAddress").value("Ben xe Mien Dong Moi"))
                .andExpect(jsonPath("$.data.pickupLatitude").value(10.879100))
                .andExpect(jsonPath("$.data.driverNote").value("Co nguoi lon tuoi Can ho tro"))
                .andExpect(jsonPath("$.data.canEditRequest").value(true));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        booking.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAndFlush(booking);
        ensureBankAccount(booking);
        PaymentRequest depositRequest = paymentRequestService.createDepositPaymentRequest(booking);
        paymentRequestService.submitPaymentProof(depositRequest.getId(), 1L,
                new SubmitProofRequest("/files/booking/proof.png", depositRequest.getAmount(),
                        "EDIT-LOCK-001", null, null));

        mockMvc.perform(patch("/api/renter/bookings/" + code + "/request-details")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isConflict());

        PaymentRequest rejected = paymentRequestRepository.findById(depositRequest.getId()).orElseThrow();
        rejected.setStatus(PaymentRequestStatus.REJECTED);
        var rejectedEvidence = paymentEvidenceRepository
                .findByPaymentRequest_IdOrderByCreatedAtDesc(depositRequest.getId()).getFirst();
        rejectedEvidence.setStatus(PaymentEvidenceStatus.REJECTED);
        paymentEvidenceRepository.saveAndFlush(rejectedEvidence);
        paymentRequestRepository.saveAndFlush(rejected);

        mockMvc.perform(patch("/api/renter/bookings/" + code + "/request-details")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEditRequest").value(true));
    }

    @Test
    @DisplayName("detail hides sequential ids and shows no refund before a deposit is paid")
    void detailHidesId() throws Exception {
        String token = login();
        for (String code : new String[] {DevDataSeeder.ACCEPTED_BOOKING_CODE, DevDataSeeder.PENDING_BOOKING_CODE}) {
            String body = mockMvc.perform(get("/api/renter/bookings/" + code)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode data = objectMapper.readTree(body).get("data");
            assertThat(data.has("id")).isFalse();
            assertThat(data.get("bookingCode").asText()).isEqualTo(code);
        }

        mockMvc.perform(get("/api/renter/bookings/" + DevDataSeeder.ACCEPTED_BOOKING_CODE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundPreview.amount").value(0))
                .andExpect(jsonPath("$.data.refundPreview.cancellable").value(true));
    }

    @Test
    @DisplayName("cancelling a pending booking records the renter and does not refund")
    void cancelPendingHasNoRefund() throws Exception {
        String token = login();
        try {
            mockMvc.perform(post("/api/renter/bookings/" + DevDataSeeder.PENDING_BOOKING_CODE + "/cancel")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelReason").value("RENTER_REQUEST"))
                    .andExpect(jsonPath("$.data.refundAmount").value(0));

            Booking booking = bookingRepository.findByBookingCode(DevDataSeeder.PENDING_BOOKING_CODE).orElseThrow();
            assertThat(booking.getCancelReason()).isEqualTo(CancelReason.RENTER_REQUEST);
            assertThat(paymentTransactionRepository.findByBooking_Id(booking.getId()))
                    .noneMatch(payment -> payment.getType() == PaymentType.REFUND);
        } finally {
            Booking booking = bookingRepository.findByBookingCode(DevDataSeeder.PENDING_BOOKING_CODE).orElseThrow();
            booking.setStatus(BookingStatus.PENDING);
            booking.setCancelReason(null);
            booking.setCancelledAt(null);
            booking.setCancelledBy(null);
            bookingRepository.saveAndFlush(booking);
        }
    }

    @Test
    @DisplayName("cancelling a confirmed daily booking more than 24 hours ahead refunds the full deposit")
    void cancelEarlyRefundsFullDeposit() throws Exception {
        String token = login();
        String code = createBooking(token, "DAILY", 1, Instant.now().plus(Duration.ofHours(72)).toString());
        accept(code);
        checkout(token, code, "CARD");

        mockMvc.perform(post("/api/renter/bookings/" + code + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(330000.0));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(paymentTransactionRepository.findByBooking_Id(booking.getId()))
                .filteredOn(payment -> payment.getType() == PaymentType.REFUND)
                .singleElement()
                .satisfies(payment -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("330000.00")));
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId()))
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(ScheduleStatus.CANCELLED));
    }

    @Test
    @DisplayName("cancelling inside 24 hours refunds half the deposit")
    void cancelLateRefundsHalfDeposit() throws Exception {
        String token = login();
        String code = createBooking(token, "DAILY", 1, "2027-08-01T01:00:00Z");
        accept(code);
        checkout(token, code, "CARD");
        Booking lateBooking = bookingRepository.findByBookingCode(code).orElseThrow();
        lateBooking.setPickupTime(Instant.now().plus(Duration.ofHours(2)));
        bookingRepository.saveAndFlush(lateBooking);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(165000.0));
    }

    @Test
    @Transactional
    @DisplayName("cancelling a confirmed yearly booking releases the schedule and does not refund")
    void cancelYearlyDoesNotRefund() throws Exception {
        String token = login("renter.demo@ecomotion.vn");
        String code = "EM-SEED-R14-001";
        checkout(token, code, "E_WALLET");

        mockMvc.perform(post("/api/renter/bookings/" + code + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundAmount").value(0));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(paymentTransactionRepository.findByBooking_Id(booking.getId()))
                .noneMatch(payment -> payment.getType() == PaymentType.REFUND);
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId()))
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(ScheduleStatus.CANCELLED));
    }

    @Test
    @DisplayName("renter confirmation waits for the provider, unless that side already confirmed")
    void confirmEndNeedsBothSides() throws Exception {
        String token = login();
        String waiting = createBooking(token, "DAILY", 1, "2026-11-08T01:00:00Z");
        accept(waiting);
        checkout(token, waiting, "CARD");
        Booking waitingBooking = bookingRepository.findByBookingCode(waiting).orElseThrow();
        waitingBooking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.saveAndFlush(waitingBooking);
        mockMvc.perform(post("/api/renter/bookings/" + waiting + "/confirm-end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completed").value(false));

        String code = createBooking(token, "DAILY", 1, "2026-11-02T01:00:00Z");
        accept(code);
        checkout(token, code, "CASH");
        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setProviderConfirmedAt(Instant.parse("2026-11-03T01:00:00Z"));
        bookingRepository.saveAndFlush(booking);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/confirm-end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AWAITING_EXTRA_CHARGE_CONFIRMATION"))
                .andExpect(jsonPath("$.data.completed").value(false));
    }

    @Test
    @DisplayName("disputes follow the category rules and hide ids")
    void openDispute() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/renter/bookings/" + DevDataSeeder.IN_PROGRESS_BOOKING_CODE + "/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"TRIP_NOT_CONFIRMED\"}"))
                .andExpect(status().isConflict());

        String body = mockMvc.perform(post("/api/renter/bookings/" + DevDataSeeder.IN_PROGRESS_BOOKING_CODE + "/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SERVICE_QUALITY\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.has("id")).isFalse();
        assertThat(data.get("status").asText()).isEqualTo("OPEN");

        mockMvc.perform(post("/api/renter/bookings/" + DevDataSeeder.IN_PROGRESS_BOOKING_CODE + "/disputes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/renter/bookings/" + DevDataSeeder.IN_PROGRESS_BOOKING_CODE + "/confirm-end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.completed").value(false));
    }

    private void checkout(String token, String code, String method) throws Exception {
        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        ensureBankAccount(booking);
        PaymentRequest request = paymentRequestService.createDepositPaymentRequest(booking);
        request.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        paymentRequestRepository.saveAndFlush(request);
        paymentRequestService.confirmPayment(
                request.getId(), 2L,
                new ConfirmPaymentRequest(request.getAmount(), "TEST-" + code, method));
    }

    private void ensureBankAccount(Booking booking) {
        if (bankAccountRepository.findByProvider_IdAndStatus(
                booking.getVehicle().getProvider().getId(), BankAccountStatus.ACTIVE).isPresent()) return;
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(providerRepository.findById(
                booking.getVehicle().getProvider().getId()).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION TEST");
        bankAccountRepository.saveAndFlush(account);
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
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
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
        return login(DevDataSeeder.RENTER_EMAIL);
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, DevDataSeeder.DEFAULT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
