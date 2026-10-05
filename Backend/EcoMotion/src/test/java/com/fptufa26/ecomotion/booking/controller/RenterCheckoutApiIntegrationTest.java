package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.entity.PaymentStatus;
import com.fptufa26.ecomotion.booking.entity.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.schedule.entity.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.entity.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RenterCheckoutApiIntegrationTest {

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
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Test
    @DisplayName("accepted daily booking pays the snapshot deposit, confirms, and locks the schedule")
    void checkoutDepositLocksSchedule() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/renter/bookings/" + DevDataSeeder.ACCEPTED_BOOKING_CODE + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upfront").value(false))
                .andExpect(jsonPath("$.data.bookingCode").value(DevDataSeeder.ACCEPTED_BOOKING_CODE));

        String code = createBooking(token, "DAILY", 3, "2026-12-05T01:00:00Z");
        accept(code);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentType").value("DEPOSIT_PAY"));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT))
                .get()
                .satisfies(charge -> {
                    assertThat(charge.getQuantity()).isNull();
                    assertThat(charge.getAmount()).isEqualByComparingTo(new BigDecimal("1305000.00"));
                });
        assertThat(paymentTransactionRepository.findByBooking_Id(booking.getId()))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getType()).isEqualTo(PaymentType.DEPOSIT_PAY);
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
                    assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("1305000.00"));
                });
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getType()).isEqualTo(ScheduleType.BOOKED);
                    assertThat(row.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);
                    assertThat(row.isExtension()).isFalse();
                });

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("checkout is rejected while the booking is still pending")
    void checkoutPendingConflicts() throws Exception {
        String token = login();
        String code = createBooking(token, "DAILY", 1, "2026-12-10T01:00:00Z");

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\"}"))
                .andExpect(status().isConflict());

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    @DisplayName("accepted yearly booking pays upfront and locks the schedule without a deposit charge")
    void checkoutYearlyUpfront() throws Exception {
        String token = login();
        String code = createBooking(token, "YEARLY", 1, "2027-06-01T01:00:00Z");
        accept(code);

        mockMvc.perform(post("/api/renter/bookings/" + code + "/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"E_WALLET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.paymentType").value("UPFRONT_PAY"))
                .andExpect(jsonPath("$.data.amountPaid").value(50000000.0));

        Booking booking = bookingRepository.findByBookingCode(code).orElseThrow();
        assertThat(bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT))
                .isEmpty();
        assertThat(vehicleScheduleRepository.findByBooking_Id(booking.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getType()).isEqualTo(ScheduleType.BOOKED));
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
