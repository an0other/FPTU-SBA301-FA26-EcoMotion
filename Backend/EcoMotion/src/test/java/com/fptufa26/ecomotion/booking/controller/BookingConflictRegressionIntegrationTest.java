package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.seed.DevDataSeeder;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.springframework.transaction.annotation.Transactional
class BookingConflictRegressionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalPlanRepository rentalPlanRepository;

    @Autowired
    private RenterRepository renterRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private com.fptufa26.ecomotion.user.repository.DriverRepository driverRepository;

    @org.junit.jupiter.api.BeforeEach
    void isolateRenterConflictScenariosFromUnfinishedSeedTrips() {
        driverRepository.findAll().forEach(driver -> {
            driver.setLicenseExpiry(java.time.LocalDate.of(2040, 12, 31));
            driver.setActivityStatus(com.fptufa26.ecomotion.user.enums.DriverActivityStatus.AVAILABLE);
        });
        driverRepository.flush();
        bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.IN_PROGRESS
                        || b.getStatus() == BookingStatus.AWAITING_END_CONFIRMATION)
                .forEach(b -> b.setStatus(BookingStatus.COMPLETED));
        bookingRepository.flush();
    }

    @Test
    @DisplayName("Đặt xe thành công với slot hợp lệ")
    void createBookingSuccessWithValidSlot() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(v -> "APPROVED".equals(v.getStatus().name()))
                .findFirst()
                .orElseThrow();
        RentalPlan daily = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name()))
                .findFirst()
                .orElseThrow();

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 3,
                  "pickupTime": "2036-10-27T14:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2,
                  "driverNote": "Dat xe cong tac"
                }
                """.formatted(vehicle.getId(), daily.getId());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.data.bookingCode").exists());
    }

    @Test
    @DisplayName("Đặt xe trùng khoảng thời gian renter đã có booking đang active -> trả 409 Conflict với thông báo rõ ràng")
    void createBookingFailsWhenRenterAlreadyHasActiveBookingInRange() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(v -> "APPROVED".equals(v.getStatus().name()))
                .findFirst()
                .orElseThrow();
        RentalPlan daily = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name()))
                .findFirst()
                .orElseThrow();

        // 1. First booking
        String body1 = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 3,
                  "pickupTime": "2037-01-10T08:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        Vehicle secondVehicle = vehicleRepository.findAll().stream()
                .filter(v -> !v.getId().equals(vehicle.getId()))
                .filter(v -> v.getProvider().getId().equals(vehicle.getProvider().getId()))
                .filter(v -> "APPROVED".equals(v.getStatus().name())).findFirst().orElseThrow();
        RentalPlan secondPlan = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(secondVehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name())).findFirst().orElseThrow();
        String body2 = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "2037-01-11T08:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(secondVehicle.getId(), secondPlan.getId());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bạn đã có đơn thuê trùng khoảng thời gian này"));
    }

    @Test
    @DisplayName("Renter có đơn PENDING cũ trong quá khứ không bị coi là xung đột khi đặt đơn mới trong tương lai")
    void createBookingSucceedsEvenIfRenterHasStalePastPendingBooking() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Renter renter = renterRepository.findAll().getFirst();
        Vehicle vehicle = vehicleRepository.findAll().getFirst();
        RentalPlan daily = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name()))
                .findFirst()
                .orElseThrow();

        // Seed a stale past PENDING booking for this renter
        Booking pastPending = new Booking();
        pastPending.setBookingCode("EM-TEST-PAST-" + UUID.randomUUID().toString().substring(0, 8));
        pastPending.setRenter(renter);
        pastPending.setVehicle(vehicle);
        pastPending.setPlan(daily);
        pastPending.setStatus(BookingStatus.PENDING);
        pastPending.setPickupAddress("Địa chỉ cũ");
        pastPending.setPickupLatitude(new BigDecimal("10.8"));
        pastPending.setPickupLongitude(new BigDecimal("106.6"));
        pastPending.setPickupTime(Instant.now().minus(Duration.ofDays(10)));
        pastPending.setPassengerCount(2);
        pastPending.setTotalAmount(new BigDecimal("1000000.00"));
        bookingRepository.save(pastPending);

        // Now book in future: must NOT be blocked by the past stale PENDING booking
        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "2038-05-01T10:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"));
    }

    @Test
    @DisplayName("Đặt xe khi xe đã có lịch bảo dưỡng/block -> trả 409 Conflict với thông báo xe bận")
    void createBookingFailsWhenVehicleHasScheduleOverlap() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(v -> "APPROVED".equals(v.getStatus().name()))
                .findFirst()
                .orElseThrow();
        RentalPlan daily = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name()))
                .findFirst()
                .orElseThrow();

        Instant blockStart = Instant.parse("2039-06-01T00:00:00Z");
        Instant blockEnd = Instant.parse("2039-06-05T00:00:00Z");

        // Book schedule for vehicle
        scheduleService.book(vehicle, null, blockStart, blockEnd);

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "2039-06-02T00:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId());

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Xe không còn trống trong khoảng thời gian đã chọn, bao gồm thời gian đệm"));
    }

    @Test
    @DisplayName("Đặt xe vượt quá số chỗ ngồi (passengerCount > seatCount - 1) -> trả 409 Conflict")
    void createBookingFailsWhenPassengerCountExceedsCapacity() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(v -> "APPROVED".equals(v.getStatus().name()))
                .findFirst()
                .orElseThrow();
        RentalPlan daily = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId()).stream()
                .filter(p -> "DAILY".equals(p.getPlanType().name()))
                .findFirst()
                .orElseThrow();

        int maxAllowed = vehicle.getSeatCount() - 1;
        int excessiveCount = vehicle.getSeatCount() + 1;

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "2040-01-01T08:00:00Z",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": %d
                }
                """.formatted(vehicle.getId(), daily.getId(), excessiveCount);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Số lượng hành khách vượt quá số chỗ ngồi khả dụng của xe (tối đa " + maxAllowed + " khách)"));
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
