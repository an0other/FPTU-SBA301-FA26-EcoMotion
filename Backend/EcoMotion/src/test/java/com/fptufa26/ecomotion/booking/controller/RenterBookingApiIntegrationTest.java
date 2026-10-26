package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.seed.DevDataSeeder;
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

import java.time.Instant;

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

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ProviderBankAccountRepository providerBankAccountRepository;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    @DisplayName("renter creates an AWAITING_DEPOSIT booking with a held schedule and VietQR request")
    void createBookingAndHoldForDeposit() throws Exception {
        String token = login();
        JsonNode vehicles = read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode vehicle = vehicles.get("data").get(0);
        long vehicleId = vehicle.get("id").asLong();
        ensureProviderBankAccount(vehicleId);
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
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "driverNote": "Co tre nho\\r\\nCan cop rong",
                  "passengerCount": 3
                }
                """.formatted(vehicleId, daily.get("id").asLong());

        long paymentsBefore = paymentTransactionRepository.count();
        String response = mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.data.bookingCode").exists())
                .andExpect(jsonPath("$.data.baseRental.chargeType").value("BASE_RENTAL"))
                .andExpect(jsonPath("$.data.baseRental.quantity").value(3))
                .andReturn().getResponse().getContentAsString();

        String bookingCode = read(response).get("data").get("bookingCode").asText();
        mockMvc.perform(get("/api/renter/bookings/" + bookingCode)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickupLatitude").value(10.801234))
                .andExpect(jsonPath("$.data.pickupLongitude").value(106.652345))
                .andExpect(jsonPath("$.data.depositAmount").isNumber())
                .andExpect(jsonPath("$.data.driverNote").value("Co tre nho Can cop rong"));

        mockMvc.perform(get("/api/vehicles/" + vehicleId + "/availability")
                        .param("from", "2026-12-01T00:00:00Z")
                        .param("to", "2026-12-05T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occupiedSlots").isNotEmpty());

        var created = bookingRepository.findByBookingCode(bookingCode).orElseThrow();
        assertThat(created.getStatus().name()).isEqualTo("AWAITING_DEPOSIT");
        assertThat(bookingChargeRepository.findAll())
                .anyMatch(charge -> charge.getChargeType() == ChargeType.BASE_RENTAL);
        assertThat(paymentTransactionRepository.count()).isEqualTo(paymentsBefore);
        var paymentRequest = paymentRequestRepository.findByBooking_IdOrderByCreatedAtDesc(created.getId())
                .getFirst();
        assertThat(vehicleScheduleRepository.findByBooking_Id(created.getId()))
                .singleElement()
                .satisfies(schedule -> {
                    assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.HELD);
                    assertThat(schedule.getExpiresAt()).isAfter(Instant.now());
                });

        paymentRequest.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        paymentRequestRepository.saveAndFlush(paymentRequest);
        long providerUserId = created.getVehicle().getProvider().getUser().getId();
        paymentRequestService.confirmPayment(paymentRequest.getId(), providerUserId,
                new ConfirmPaymentRequest(paymentRequest.getAmount(), "BANK-CREATE-HOLD-001", null));

        assertThat(bookingRepository.findById(created.getId()).orElseThrow().getStatus().name())
                .isEqualTo("CONFIRMED");
        assertThat(vehicleScheduleRepository.findByBooking_Id(created.getId()))
                .singleElement()
                .satisfies(schedule -> {
                    assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
                    assertThat(schedule.getExpiresAt()).isNull();
                });
        assertThat(bookingChargeRepository.findByBooking_IdAndChargeType(created.getId(), ChargeType.DEPOSIT))
                .isEmpty();
        assertThat(paymentTransactionRepository.findByBooking_Id(created.getId()))
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getAmount()).isEqualByComparingTo(paymentRequest.getAmount());
                    assertThat(transaction.getAppliedAmount()).isEqualByComparingTo(paymentRequest.getAmount());
                });
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

    @Test
    @DisplayName("booking must be created at least three hours before pickup")
    void bookingUnderThreeHoursIsRejected() throws Exception {
        String token = login();
        JsonNode vehicle = firstVehicle(token);
        JsonNode daily = plan(vehicle, "DAILY");
        String pickupTime = Instant.now().plusSeconds(2 * 60 * 60).toString();

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(vehicle, daily, pickupTime,
                                "\"pickupLatitude\":10.8,\"pickupLongitude\":106.65,")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("hourly bookings cannot exceed twenty four hours")
    void hourlyBookingOverTwentyFourHoursIsRejected() throws Exception {
        String token = login();
        JsonNode vehicle = firstVehicle(token);
        JsonNode hourly = plan(vehicle, "HOURLY");
        String body = """
                {"vehicleId":%d,"planId":%d,"slotCount":25,"pickupTime":"2035-08-01T01:00:00Z",
                 "pickupAddress":"18 Hoang Hoa Tham, Tan Binh","pickupLatitude":10.8,
                 "pickupLongitude":106.65,"passengerCount":2}
                """.formatted(vehicle.get("id").asLong(), hourly.get("id").asLong());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
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
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": %d
                }
                """.formatted(vehicle.get("id").asLong(), daily.get("id").asLong(), seatCount);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("new renter bookings require a complete in-range pickup coordinate pair")
    void pickupCoordinatesMustBeCompleteAndInRange() throws Exception {
        String token = login();
        JsonNode vehicle = firstVehicle(token);
        JsonNode daily = plan(vehicle, "DAILY");
        long before = bookingRepository.count();

        String missingLongitude = bookingBody(vehicle, daily, "2035-05-01T01:00:00Z",
                "\"pickupLatitude\":10.8,");
        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingLongitude))
                .andExpect(status().isBadRequest());

        String invalidLatitude = bookingBody(vehicle, daily, "2035-05-02T01:00:00Z",
                "\"pickupLatitude\":91,\"pickupLongitude\":106.65,");
        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidLatitude))
                .andExpect(status().isBadRequest());

        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("generic booking rejects past pickup time and yearly plans")
    void genericBookingRejectsPastAndYearly() throws Exception {
        String token = login();
        JsonNode vehicle = firstVehicle(token);
        long before = bookingRepository.count();

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(vehicle, plan(vehicle, "DAILY"), "2020-01-01T01:00:00Z",
                                "\"pickupLatitude\":10.8,\"pickupLongitude\":106.65,")))
                .andExpect(status().isConflict());

        JsonNode yearly = plan(vehicle, "YEARLY");
        if (yearly != null) {
            mockMvc.perform(post("/api/renter/bookings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingBody(vehicle, yearly, "2035-06-01T01:00:00Z",
                                    "\"pickupLatitude\":10.8,\"pickupLongitude\":106.65,")))
                    .andExpect(status().isConflict());
        }

        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("driver note longer than 160 normalized characters is rejected")
    void driverNoteHasOneLineLimit() throws Exception {
        String token = login();
        JsonNode vehicle = firstVehicle(token);
        JsonNode daily = plan(vehicle, "DAILY");
        String longNote = "x".repeat(161);
        String body = bookingBody(vehicle, daily, "2035-07-01T01:00:00Z",
                "\"pickupLatitude\":10.8,\"pickupLongitude\":106.65,\"driverNote\":\"" + longNote + "\",");

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private JsonNode firstVehicle(String token) throws Exception {
        return read(mockMvc.perform(get("/api/renter/bookings/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data").get(0);
    }

    private JsonNode plan(JsonNode vehicle, String type) {
        for (JsonNode candidate : vehicle.get("plans")) {
            if (type.equals(candidate.get("planType").asText())) return candidate;
        }
        return null;
    }

    private String bookingBody(JsonNode vehicle, JsonNode plan, String pickupTime, String extraFields) {
        assertThat(plan).isNotNull();
        return """
                {"vehicleId":%d,"planId":%d,"slotCount":1,"pickupTime":"%s",
                 "pickupAddress":"18 Hoang Hoa Tham, Tan Binh",%s"passengerCount":2}
                """.formatted(vehicle.get("id").asLong(), plan.get("id").asLong(), pickupTime, extraFields);
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
