package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
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
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
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
    private VehicleRepository vehicleRepository;
    private VehicleCatalogService vehicleCatalogService;
    private UserService userService;
    private ProviderScheduleServiceImpl providerScheduleService;

    private Provider provider;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicleScheduleRepository = mock(VehicleScheduleRepository.class);
        vehicleRepository = mock(VehicleRepository.class);
        vehicleCatalogService = mock(VehicleCatalogService.class);
        userService = mock(UserService.class);
        providerScheduleService = new ProviderScheduleServiceImpl(
                vehicleScheduleRepository,
                vehicleRepository,
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
        when(vehicleRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(vehicle));
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

        when(vehicleScheduleRepository.findOccupiedInRange(eq(100L), any(), any()))
                .thenReturn(Collections.emptyList());
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

        when(vehicleScheduleRepository.findOccupiedInRange(eq(100L), any(), any()))
                .thenReturn(Collections.emptyList());
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

    @Test
    void createBlockSchedule_whenConsecutiveInternalBlock_success() {
        vehicle.setStatus(VehicleStatus.APPROVED);
        Instant base = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant firstStart = base;
        Instant firstEnd = base.plus(2, ChronoUnit.HOURS);
        Instant secondStart = firstEnd; // Exactly touches firstEnd
        Instant secondEnd = secondStart.plus(2, ChronoUnit.HOURS);

        VehicleSchedule existingMaintenance = new VehicleSchedule();
        existingMaintenance.setId(201L);
        existingMaintenance.setVehicle(vehicle);
        existingMaintenance.setType(ScheduleType.MAINTENANCE);
        existingMaintenance.setStartAt(firstStart);
        existingMaintenance.setEndAt(firstEnd);
        existingMaintenance.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findOccupiedInRange(eq(100L), any(), any()))
                .thenReturn(List.of(existingMaintenance));
        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenAnswer(inv -> {
            VehicleSchedule saved = inv.getArgument(0);
            saved.setId(502L);
            return saved;
        });

        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.BLOCKED,
                secondStart,
                secondEnd,
                "Đi việc riêng tiếp sau bảo dưỡng"
        );

        ProviderScheduleItemResponse response = providerScheduleService.createBlockSchedule(1L, request);

        assertThat(response.id()).isEqualTo(502L);
        assertThat(response.type()).isEqualTo(ScheduleType.BLOCKED);
    }

    @Test
    void createBlockSchedule_whenOverlappingInternalBlock_throwsConflictException() {
        vehicle.setStatus(VehicleStatus.APPROVED);
        Instant base = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant existingStart = base;
        Instant existingEnd = base.plus(3, ChronoUnit.HOURS);

        VehicleSchedule existingMaintenance = new VehicleSchedule();
        existingMaintenance.setId(201L);
        existingMaintenance.setVehicle(vehicle);
        existingMaintenance.setType(ScheduleType.MAINTENANCE);
        existingMaintenance.setStartAt(existingStart);
        existingMaintenance.setEndAt(existingEnd);
        existingMaintenance.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findOccupiedInRange(eq(100L), any(), any()))
                .thenReturn(List.of(existingMaintenance));

        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.BLOCKED,
                existingStart.plus(1, ChronoUnit.HOURS),
                existingEnd.plus(1, ChronoUnit.HOURS),
                "Chồng lấn lịch bảo dưỡng"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Không thể chặn lịch vì trùng với lịch bảo dưỡng hiện có");
    }

    @Test
    void createBlockSchedule_whenOverlappingBookedScheduleWithBuffer_throwsConflictException() {
        vehicle.setStatus(VehicleStatus.APPROVED);
        Instant base = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant bookedStart = base;
        Instant bookedEnd = base.plus(3, ChronoUnit.HOURS);

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setBookingCode("EM-2026-000099");

        VehicleSchedule bookedSchedule = new VehicleSchedule();
        bookedSchedule.setId(301L);
        bookedSchedule.setVehicle(vehicle);
        bookedSchedule.setType(ScheduleType.BOOKED);
        bookedSchedule.setBooking(booking);
        bookedSchedule.setStartAt(bookedStart);
        bookedSchedule.setEndAt(bookedEnd);
        bookedSchedule.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findOccupiedInRange(eq(100L), any(), any()))
                .thenReturn(List.of(bookedSchedule));

        // Attempting to block within the 60-minute buffer after bookedEnd
        CreateBlockScheduleRequest request = new CreateBlockScheduleRequest(
                100L,
                ScheduleType.MAINTENANCE,
                bookedEnd.plus(30, ChronoUnit.MINUTES),
                bookedEnd.plus(2, ChronoUnit.HOURS),
                "Chặn trong vùng đệm của đơn khách"
        );

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Không thể chặn lịch vì trùng với đơn đặt xe #EM-2026-000099")
                .hasMessageContaining("kèm 60 phút đệm kỹ thuật");
    }

    @Test
    void cancelBlockSchedule_whenPastEndAt_throwsBadRequestException() {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setId(401L);
        schedule.setVehicle(vehicle);
        schedule.setType(ScheduleType.MAINTENANCE);
        schedule.setStartAt(Instant.now().minus(3, ChronoUnit.HOURS));
        schedule.setEndAt(Instant.now().minus(1, ChronoUnit.HOURS));
        schedule.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findById(401L)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> providerScheduleService.cancelBlockSchedule(1L, 401L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Lịch chặn này đã kết thúc, hệ thống đã tự động mở lại lịch xe");
    }

    @Test
    void cancelBlockSchedule_whenFutureEndAt_success() {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setId(402L);
        schedule.setVehicle(vehicle);
        schedule.setType(ScheduleType.MAINTENANCE);
        schedule.setStartAt(Instant.now().plus(1, ChronoUnit.HOURS));
        schedule.setEndAt(Instant.now().plus(3, ChronoUnit.HOURS));
        schedule.setStatus(ScheduleStatus.ACTIVE);

        when(vehicleScheduleRepository.findById(402L)).thenReturn(Optional.of(schedule));
        when(vehicleScheduleRepository.save(any(VehicleSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        ProviderScheduleItemResponse response = providerScheduleService.cancelBlockSchedule(1L, 402L);

        assertThat(response.id()).isEqualTo(402L);
        assertThat(response.status()).isEqualTo(ScheduleStatus.CANCELLED);
    }
}
