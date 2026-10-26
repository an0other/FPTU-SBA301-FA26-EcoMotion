package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.implement.VehicleOperationalRiskServiceImpl;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleOperationalRiskServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private VehicleOperationalRiskServiceImpl service;

    @Test
    void marksUpcomingBookingAtRiskWhenPreviousTripHasNotEnded() {
        Vehicle vehicle = new Vehicle();
        ReflectionTestUtils.setField(vehicle, "id", 10L);

        Booking currentTrip = booking("BK-CURRENT", vehicle, Instant.parse("2026-10-23T01:00:00Z"));
        currentTrip.setStatus(BookingStatus.IN_PROGRESS);
        Booking upcoming = booking("BK-NEXT", vehicle, Instant.parse("2026-10-23T04:00:00Z"));
        upcoming.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByVehicle_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(currentTrip, upcoming));

        var result = service.assess(upcoming);

        assertThat(result.atRisk()).isTrue();
        assertThat(result.blockingBookingCode()).isEqualTo("BK-CURRENT");
        assertThat(result.message()).contains("chưa kết thúc");
    }

    @Test
    void doesNotMarkBookingAtRiskAfterPreviousTripEnded() {
        Vehicle vehicle = new Vehicle();
        ReflectionTestUtils.setField(vehicle, "id", 10L);

        Booking previous = booking("BK-DONE", vehicle, Instant.parse("2026-10-23T01:00:00Z"));
        previous.setStatus(BookingStatus.COMPLETED);
        previous.setActualEndAt(Instant.parse("2026-10-23T03:00:00Z"));
        Booking upcoming = booking("BK-NEXT", vehicle, Instant.parse("2026-10-23T04:00:00Z"));
        upcoming.setStatus(BookingStatus.CONFIRMED);

        when(bookingRepository.findByVehicle_IdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(upcoming));

        assertThat(service.assess(upcoming).atRisk()).isFalse();
    }

    private Booking booking(String code, Vehicle vehicle, Instant pickupTime) {
        Booking booking = new Booking();
        ReflectionTestUtils.setField(booking, "id", (long) code.hashCode());
        booking.setBookingCode(code);
        booking.setVehicle(vehicle);
        booking.setPickupTime(pickupTime);
        return booking;
    }
}
