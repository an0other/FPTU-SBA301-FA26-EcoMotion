package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleItemResponse;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleStatsResponse;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.implement.ProviderScheduleServiceImpl;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderScheduleServiceTest {

    @Mock
    private VehicleScheduleRepository vehicleScheduleRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleCatalogService vehicleCatalogService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProviderScheduleServiceImpl providerScheduleService;

    private Provider mockProvider;
    private Vehicle mockVehicle;
    private final Long userId = 100L;
    private final Long providerId = 1L;
    private final Long vehicleId = 10L;

    @BeforeEach
    void setUp() {
        mockProvider = new Provider();
        mockProvider.setId(providerId);

        mockVehicle = new Vehicle();
        mockVehicle.setId(vehicleId);
        mockVehicle.setProvider(mockProvider);
        mockVehicle.setLicensePlate("51A-111.11");
        mockVehicle.setBrand("VinFast");
        mockVehicle.setModel("VF 6");
    }

    @Test
    @DisplayName("getScheduleStats - Returns 4 accurate KPI numbers")
    void getScheduleStats_Success() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));
        when(vehicleScheduleRepository.countActiveBlocksByProviderId(eq(providerId), any(Instant.class))).thenReturn(6L);
        when(vehicleScheduleRepository.countActiveMaintenanceByProviderId(eq(providerId), any(Instant.class))).thenReturn(2L);
        when(vehicleScheduleRepository.countActiveBlockedByProviderId(eq(providerId), any(Instant.class))).thenReturn(4L);
        when(vehicleScheduleRepository.countUpcomingBookingsByProviderId(eq(providerId), any(Instant.class))).thenReturn(18L);

        ProviderScheduleStatsResponse stats = providerScheduleService.getScheduleStats(userId);

        assertThat(stats).isNotNull();
        assertThat(stats.totalActiveBlocks()).isEqualTo(6L);
        assertThat(stats.activeMaintenance()).isEqualTo(2L);
        assertThat(stats.activeBlocked()).isEqualTo(4L);
        assertThat(stats.upcomingBookings()).isEqualTo(18L);
    }

    @Test
    @DisplayName("countCurrentlyBlockedVehiclesByProviderId - Returns accurate count")
    void countCurrentlyBlockedVehiclesByProviderId_Success() {
        when(vehicleScheduleRepository.countCurrentlyBlockedVehiclesByProviderId(eq(providerId), any(Instant.class)))
                .thenReturn(1L);

        long result = providerScheduleService.countCurrentlyBlockedVehiclesByProviderId(providerId);

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("getProviderSchedules - Filters schedules by type and status correctly")
    void getProviderSchedules_Success_Filtered() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        VehicleSchedule s1 = new VehicleSchedule();
        s1.setId(1L);
        s1.setVehicle(mockVehicle);
        s1.setType(ScheduleType.MAINTENANCE);
        s1.setStatus(ScheduleStatus.ACTIVE);
        s1.setStartAt(Instant.now());
        s1.setEndAt(Instant.now().plus(4, ChronoUnit.HOURS));

        VehicleSchedule s2 = new VehicleSchedule();
        s2.setId(2L);
        s2.setVehicle(mockVehicle);
        s2.setType(ScheduleType.BLOCKED);
        s2.setStatus(ScheduleStatus.CANCELLED);
        s2.setStartAt(Instant.now());
        s2.setEndAt(Instant.now().plus(4, ChronoUnit.HOURS));

        when(vehicleScheduleRepository.findByProviderIdOrderDesc(providerId)).thenReturn(List.of(s1, s2));

        List<ProviderScheduleItemResponse> results = providerScheduleService.getProviderSchedules(
                userId,
                vehicleId,
                ScheduleType.MAINTENANCE,
                ScheduleStatus.ACTIVE,
                null,
                null
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(1L);
        assertThat(results.get(0).type()).isEqualTo(ScheduleType.MAINTENANCE);
        assertThat(results.get(0).status()).isEqualTo(ScheduleStatus.ACTIVE);
    }

    @Test
    @DisplayName("createBlockSchedule - Throws BadRequest when type is BOOKED")
    void createBlockSchedule_ThrowsBadRequest_WhenTypeIsBooked() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.BOOKED,
                start,
                end,
                "Khách đặt"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể tự tạo lịch loại đơn đặt xe");
    }

    @Test
    @DisplayName("createBlockSchedule - Throws BadRequest when endAt <= startAt")
    void createBlockSchedule_ThrowsBadRequest_WhenEndBeforeStart() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.minus(1, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng pin"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Thời gian kết thúc phải lớn hơn thời gian bắt đầu");
    }

    @Test
    @DisplayName("createBlockSchedule - Throws BadRequest when startAt is in the past")
    void createBlockSchedule_ThrowsBadRequest_WhenStartAtInPast() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng quá khứ"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Thời gian bắt đầu không được ở trong quá khứ");
    }

    @Test
    @DisplayName("createBlockSchedule - Throws ConflictException when customer booking overlaps")
    void createBlockSchedule_ThrowsConflict_WhenBookingOverlaps() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));
        when(vehicleCatalogService.requireOwnedByProvider(vehicleId, providerId)).thenReturn(mockVehicle);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng định kỳ"
        );

        when(vehicleScheduleRepository.existsActiveOverlap(eq(vehicleId), any(Instant.class), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Không thể chặn lịch");
    }

    @Test
    @DisplayName("createBlockSchedule - Throws ConflictException when existing block overlaps")
    void createBlockSchedule_ThrowsConflict_WhenBlockOverlaps() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));
        when(vehicleCatalogService.requireOwnedByProvider(vehicleId, providerId)).thenReturn(mockVehicle);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.BLOCKED,
                start,
                end,
                "Bận việc riêng"
        );

        when(vehicleScheduleRepository.existsActiveOverlap(eq(vehicleId), any(Instant.class), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Không thể chặn lịch");
    }

    @Test
    @DisplayName("createBlockSchedule - Successfully saves schedule when valid")
    void createBlockSchedule_Success() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));
        when(vehicleCatalogService.requireOwnedByProvider(vehicleId, providerId)).thenReturn(mockVehicle);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                vehicleId,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng pin định kỳ 10.000km"
        );

        VehicleSchedule savedSchedule = new VehicleSchedule();
        savedSchedule.setId(101L);
        savedSchedule.setVehicle(mockVehicle);
        savedSchedule.setType(ScheduleType.MAINTENANCE);
        savedSchedule.setStartAt(start);
        savedSchedule.setEndAt(end);
        savedSchedule.setStatus(ScheduleStatus.ACTIVE);
        savedSchedule.setReason("Bảo dưỡng pin định kỳ 10.000km");

        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenReturn(savedSchedule);

        ProviderScheduleItemResponse response = providerScheduleService.createBlockSchedule(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.vehicleLicensePlate()).isEqualTo("51A-111.11");
        assertThat(response.type()).isEqualTo(ScheduleType.MAINTENANCE);
        assertThat(response.status()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(response.reason()).isEqualTo("Bảo dưỡng pin định kỳ 10.000km");
    }

    @Test
    @DisplayName("cancelBlockSchedule - Successfully cancels ACTIVE schedule")
    void cancelBlockSchedule_Success() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setId(102L);
        schedule.setVehicle(mockVehicle);
        schedule.setType(ScheduleType.BLOCKED);
        schedule.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findById(102L)).thenReturn(Optional.of(schedule));
        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderScheduleItemResponse response = providerScheduleService.cancelBlockSchedule(userId, 102L);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ScheduleStatus.CANCELLED);
        verify(vehicleScheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("cancelBlockSchedule - Throws BadRequest when trying to cancel BOOKED schedule")
    void cancelBlockSchedule_ThrowsBadRequest_WhenCancelingBookedSchedule() {
        when(userService.findProviderByUserId(userId)).thenReturn(Optional.of(mockProvider));

        VehicleSchedule bookedSchedule = new VehicleSchedule();
        bookedSchedule.setId(103L);
        bookedSchedule.setVehicle(mockVehicle);
        bookedSchedule.setType(ScheduleType.BOOKED);
        bookedSchedule.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findById(103L)).thenReturn(Optional.of(bookedSchedule));

        assertThatThrownBy(() -> providerScheduleService.cancelBlockSchedule(userId, 103L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể hủy lịch đặt xe của khách");
    }
}
