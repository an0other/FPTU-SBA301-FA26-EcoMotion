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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@SpringBootTest(properties = {"booking.max-advance-months=300"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.springframework.transaction.annotation.Transactional
class BookingConflictRegressionIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeFixture {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock regressionClock() {
            return java.time.Clock.fixed(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private java.time.Clock clock;
    private Instant now;
    private Instant pickup;

    @Autowired
    private com.fptufa26.ecomotion.schedule.service.ProviderScheduleService providerSchedules;
    @Autowired
    private com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository schedules;

    @MockitoBean
    private com.fptufa26.ecomotion.location.service.implement.VietmapClient vietmap;

    @BeforeEach
    void roadRoutingFixture() {
        now = clock.instant();
        pickup = now.plus(Duration.ofDays(45));
        org.mockito.Mockito.when(vietmap.route(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new com.fptufa26.ecomotion.location.dto.response.RoadRouteResponse(4500, 600,
                        java.util.List.of(java.util.List.of(106.7, 10.8), java.util.List.of(106.652345, 10.801234)),
                        "VIETMAP_CAR", java.time.Instant.now()));
    }


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

    @BeforeEach
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
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2,
                  "driverNote": "Dat xe cong tac"
                }
                """.formatted(vehicle.getId(), daily.getId(), pickup);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.data.bookingCode").exists())
                .andDo(result -> assertPersistedHold(result.getResponse().getContentAsString()));
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
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId(), pickup);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());

        long bookingsBefore = bookingRepository.count();
        long schedulesBefore = schedules.count();

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
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(secondVehicle.getId(), secondPlan.getId(), pickup.plus(Duration.ofDays(1)));

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Bạn đã có đơn thuê trùng khoảng thời gian này"));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(schedules.count()).isEqualTo(schedulesBefore);
    }

    @Test
    @DisplayName("Renter có đơn PENDING cũ trong quá khứ không bị coi là xung đột khi đặt đơn mới trong tương lai")
    void createBookingSucceedsEvenIfRenterHasStalePastPendingBooking() throws Exception {
        String token = login(DevDataSeeder.RENTER_EMAIL);
        Renter renter = renterRepository.findAll().stream()
                .filter(r -> DevDataSeeder.RENTER_EMAIL.equals(r.getUser().getEmail())).findFirst().orElseThrow();
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
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId(), pickup);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AWAITING_DEPOSIT"))
                .andDo(result -> assertPersistedHold(result.getResponse().getContentAsString()));
        assertThat(bookingRepository.findById(pastPending.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
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

        Instant blockStart = pickup.minus(Duration.ofDays(1));
        Instant blockEnd = pickup.plus(Duration.ofDays(3));

        // Book schedule for vehicle
        providerSchedules.createBlockSchedule(vehicle.getProvider().getUser().getId(),
                new com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest(vehicle.getId(),
                        com.fptufa26.ecomotion.schedule.enums.ScheduleType.MAINTENANCE, blockStart, blockEnd, "QA maintenance"));
        long bookingsBefore = bookingRepository.count();
        long schedulesBefore = schedules.count();

        String body = """
                {
                  "vehicleId": %d,
                  "planId": %d,
                  "slotCount": 1,
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": 2
                }
                """.formatted(vehicle.getId(), daily.getId(), pickup);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Xe không còn trống trong khoảng thời gian đã chọn, bao gồm thời gian đệm"));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
        assertThat(schedules.count()).isEqualTo(schedulesBefore);
        assertThat(scheduleService.isRangeFree(vehicle.getId(), blockStart, blockEnd)).isFalse();
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
                  "pickupTime": "%s",
                  "pickupAddress": "18 Hoang Hoa Tham, Tan Binh",
                  "pickupLatitude": 10.801234,
                  "pickupLongitude": 106.652345,
                  "passengerCount": %d
                }
                """.formatted(vehicle.getId(), daily.getId(), pickup, excessiveCount);

        mockMvc.perform(post("/api/renter/bookings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Số lượng hành khách vượt quá số chỗ ngồi khả dụng của xe (tối đa " + maxAllowed + " khách)"));
    }

    private void assertPersistedHold(String response) throws Exception {
        String code = objectMapper.readTree(response).get("data").get("bookingCode").asText();
        Booking created = bookingRepository.findAll().stream().filter(b -> code.equals(b.getBookingCode())).findFirst().orElseThrow();
        assertThat(created.getStatus()).isEqualTo(BookingStatus.AWAITING_DEPOSIT);
        assertThat(schedules.findByBooking_Id(created.getId())).singleElement().satisfies(hold -> {
            assertThat(hold.getStatus()).isEqualTo(com.fptufa26.ecomotion.schedule.enums.ScheduleStatus.HELD);
            assertThat(hold.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        });
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
