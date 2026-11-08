package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleItemResponse;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.implement.ProviderScheduleServiceImpl;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderScheduleServiceImplTest {

    private VehicleScheduleRepository vehicleScheduleRepository;
    private VehicleCatalogService vehicleCatalogService;
    private UserService userService;
    private ProviderScheduleServiceImpl providerScheduleService;

    private Provider provider;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicleScheduleRepository = mock(VehicleScheduleRepository.class);
        vehicleCatalogService = mock(VehicleCatalogService.class);
        userService = mock(UserService.class);
        providerScheduleService = new ProviderScheduleServiceImpl(
                vehicleScheduleRepository,
                vehicleCatalogService,
                userService
        );

        provider = new Provider();
        provider.setId(10L);

        vehicle = new Vehicle();
        vehicle.setId(100L);
        vehicle.setProvider(provider);
        vehicle.setLicensePlate("51K-334.12");
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF 8");
        vehicle.setMaintenanceBufferMinutes(60);

        when(userService.findProviderByUserId(1L)).thenReturn(Optional.of(provider));
        when(vehicleCatalogService.requireOwnedByProvider(100L, 10L)).thenReturn(vehicle);
    }

    @Test
    void createBlockSchedule_whenVehiclePending_throwsBadRequestException() {
        vehicle.setStatus(VehicleStatus.PENDING);
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng định kỳ"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phương tiện đang chờ Admin duyệt hồ sơ, chưa thể tạo lịch chặn hoặc bảo dưỡng");

        verify(vehicleScheduleRepository, never()).save(any());
    }

    @Test
    void createBlockSchedule_whenVehicleRejected_throwsBadRequestException() {
        vehicle.setStatus(VehicleStatus.REJECTED);
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.BLOCKED,
                start,
                end,
                "Việc cá nhân"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phương tiện đã bị từ chối phê duyệt, không thể tạo lịch chặn hoặc bảo dưỡng");

        verify(vehicleScheduleRepository, never()).save(any());
    }

    @Test
    void createBlockSchedule_whenVehicleApproved_success() {
        vehicle.setStatus(VehicleStatus.APPROVED);
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo dưỡng pin"
        );

        when(vehicleScheduleRepository.existsActiveOverlap(eq(100L), any(), any())).thenReturn(false);
        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenAnswer(inv -> {
            VehicleSchedule saved = inv.getArgument(0);
            saved.setId(500L);
            return saved;
        });

        ProviderScheduleItemResponse response = providerScheduleService.createBlockSchedule(1L, request);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.vehicleId()).isEqualTo(100L);
        assertThat(response.type()).isEqualTo(ScheduleType.MAINTENANCE);
        assertThat(response.status()).isEqualTo(ScheduleStatus.ACTIVE);
        assertThat(response.reason()).isEqualTo("Bảo dưỡng pin");
    }

    @Test
    void createBlockSchedule_whenVehicleHidden_success() {
        vehicle.setStatus(VehicleStatus.HIDDEN);
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.MAINTENANCE,
                start,
                end,
                "Bảo trì nội bộ khi ẩn xe"
        );

        when(vehicleScheduleRepository.existsActiveOverlap(eq(100L), any(), any())).thenReturn(false);
        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenAnswer(inv -> {
            VehicleSchedule saved = inv.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        ProviderScheduleItemResponse response = providerScheduleService.createBlockSchedule(1L, request);

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.vehicleId()).isEqualTo(100L);
        assertThat(response.type()).isEqualTo(ScheduleType.MAINTENANCE);
        assertThat(response.status()).isEqualTo(ScheduleStatus.ACTIVE);
    }
}

