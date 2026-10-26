package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.implement.DriverAvailabilityServiceImpl;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverAvailabilityServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingPolicySnapshotRepository snapshotRepository;
    @Mock
    private BookingChargeRepository chargeRepository;
    @Mock
    private DriverAssignmentService driverAssignmentService;

    @InjectMocks
    private DriverAvailabilityServiceImpl service;

    @Test
    void eligibleDriverWithoutOverlapIsAvailable() {
        Driver driver = new Driver();
        driver.setId(11L);
        Vehicle vehicle = vehicle(5L, 30);
        Instant start = Instant.parse("2027-01-02T01:00:00Z");
        Instant end = start.plusSeconds(3600);

        when(driverAssignmentService.listEligible(eq(3L), any())).thenReturn(List.of(driver));
        when(bookingRepository.findPotentialDriverConflicts(eq(11L), any(), eq(null), any()))
                .thenReturn(List.of());

        assertThat(service.findAvailable(3L, vehicle, start, end, null)).containsExactly(driver);
    }

    @Test
    void tripWithoutActualEndKeepsDriverUnavailable() {
        Driver driver = new Driver();
        driver.setId(11L);
        Vehicle vehicle = vehicle(5L, 30);
        Booking running = new Booking();
        running.setStatus(BookingStatus.IN_PROGRESS);
        running.setActualEndAt(null);

        when(bookingRepository.findPotentialDriverConflicts(eq(11L), any(), eq(99L), any()))
                .thenReturn(List.of(running));

        assertThat(service.isAvailable(
                driver.getId(), vehicle,
                Instant.parse("2027-01-02T01:00:00Z"),
                Instant.parse("2027-01-02T02:00:00Z"), 99L)).isFalse();
    }

    private Vehicle vehicle(Long id, int bufferMinutes) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setMaintenanceBufferMinutes(bufferMinutes);
        return vehicle;
    }
}
