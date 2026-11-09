package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.TripConflictException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tài xế đang chạy chuyến DF-2026-9003 thì không được báo đến / bắt đầu chuyến EM-2026-000043. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DriverSingleActiveTripTest {

    @Mock BookingRepository bookingRepository;
    @Mock UserService userService;
    @InjectMocks DriverTripServiceImpl service;

    private Booking booking;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        Driver driver = new Driver();
        driver.setId(7L);
        driver.setStatus(DriverStatus.APPROVED);
        driver.setUser(user);
        when(userService.findDriverProfile(70L)).thenReturn(Optional.of(driver));

        booking = new Booking();
        booking.setId(43L);
        booking.setBookingCode("EM-2026-000043");
        booking.setDriver(driver);
        booking.setDriverConfirmedAt(Instant.parse("2026-11-09T05:00:00Z"));
        when(bookingRepository.findByBookingCodeForUpdate("EM-2026-000043")).thenReturn(Optional.of(booking));
        when(bookingRepository.findOtherActiveDriverBookingCodes(eq(7L), eq(43L), anyCollection()))
                .thenReturn(List.of("DF-2026-9003"));
    }

    @Test
    void arriveIsRejectedWhileAnotherTripIsActive() {
        booking.setStatus(BookingStatus.DRIVER_CONFIRMED);
        assertThatThrownBy(() -> service.arriveAtPickup(70L, "EM-2026-000043"))
                .isInstanceOf(TripConflictException.class)
                .hasMessageContaining("DF-2026-9003");
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void startIsRejectedWhileAnotherTripIsActive() {
        booking.setStatus(BookingStatus.DRIVER_ARRIVED);
        booking.setActualArrivedAt(Instant.parse("2026-11-09T05:30:00Z"));
        assertThatThrownBy(() -> service.startTrip(70L, "EM-2026-000043"))
                .isInstanceOf(TripConflictException.class)
                .hasMessageContaining("DF-2026-9003");
        verify(bookingRepository, never()).saveAndFlush(any());
    }
}
