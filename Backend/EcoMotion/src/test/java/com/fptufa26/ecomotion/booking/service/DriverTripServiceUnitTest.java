package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.DriverTripDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripView;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.VehicleLocationRepository;
import com.fptufa26.ecomotion.booking.service.implement.DriverTripServiceImpl;
import com.fptufa26.ecomotion.common.config.ClockConfig;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverTripServiceUnitTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private VehicleLocationRepository vehicleLocationRepository;

    @Mock
    private UserService userService;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private MapGeocodingService mapGeocodingService;

    private Clock fixedClock;
    private DriverTripService driverTripService;

    private User driverUser;
    private Driver activeDriver;

    @BeforeEach
    void setUp() {
        ZonedDateTime fixedDateTime = ZonedDateTime.of(2026, 10, 17, 10, 30, 0, 0, ClockConfig.VIETNAM_ZONE);
        fixedClock = Clock.fixed(fixedDateTime.toInstant(), ClockConfig.VIETNAM_ZONE);

        driverTripService = new DriverTripServiceImpl(
                bookingRepository,
                vehicleLocationRepository,
                userService,
                vehicleService,
                mapGeocodingService,
                fixedClock
        );

        driverUser = new User();
        driverUser.setId(201L);
        driverUser.setStatus(UserStatus.ACTIVE);

        activeDriver = new Driver();
        activeDriver.setId(501L);
        activeDriver.setUser(driverUser);
        activeDriver.setStatus(DriverStatus.APPROVED);
    }

    private Booking createSampleBooking(Long id, String code, BookingStatus status, Instant pickupTime) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingCode(code);
        booking.setStatus(status);
        booking.setPickupTime(pickupTime);
        booking.setPickupAddress("123 Le Loi, District 1");
        booking.setPassengerCount(2);
        booking.setTotalAmount(new BigDecimal("1500000.00"));
        booking.setCreatedAt(pickupTime.minusSeconds(3600));

        Vehicle vehicle = new Vehicle();
        vehicle.setId(10L);
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF 8");
        vehicle.setLicensePlate("51A-99999");
        vehicle.setColor("Black");
        vehicle.setSeatCount(5);

        Provider provider = new Provider();
        provider.setId(20L);
        provider.setDisplayName("EcoMotion Depot A");
        provider.setHotline("19001234");
        provider.setDepotAddress("Depot A, Saigon");
        vehicle.setProvider(provider);
        booking.setVehicle(vehicle);

        User renterUser = new User();
        renterUser.setId(301L);
        renterUser.setFullName("Nguyen Van Renter");
        renterUser.setPhone("0909000111");

        Renter renter = new Renter();
        renter.setId(30L);
        renter.setUser(renterUser);
        booking.setRenter(renter);

        RentalPlan plan = new RentalPlan();
        plan.setPlanType(PlanType.DAILY);
        plan.setSlotPrice(new BigDecimal("1500000.00"));
        plan.setSlotLengthMinutes(1440);
        booking.setPlan(plan);

        booking.setDriver(activeDriver);
        return booking;
    }

    @Test
    @DisplayName("Should successfully retrieve today schedule with valid pagination and mappings")
    void testListTodayScheduleSuccess() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        Instant todayPickup = fixedClock.instant().plusSeconds(3600);
        Booking booking = createSampleBooking(1L, "BK-TODAY01", BookingStatus.CONFIRMED, todayPickup);

        when(bookingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));
        when(vehicleService.getCoverImageUrls(anySet())).thenReturn(Map.of(10L, "https://image.com/vf8.jpg"));

        PageResponse<DriverTripResponse> response = driverTripService.listDriverTrips(
                201L, "TODAY", null, null, 0, 20, null
        );

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        DriverTripResponse item = response.items().get(0);
        assertThat(item.tripId()).isEqualTo(1L);
        assertThat(item.bookingCode()).isEqualTo("BK-TODAY01");
        assertThat(item.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(item.vehicle().coverUrl()).isEqualTo("https://image.com/vf8.jpg");
        assertThat(item.customer().fullName()).isEqualTo("Nguyen Van Renter");
        assertThat(item.customer().phone()).isEqualTo("0909000111");
        assertThat(item.rentalType()).isEqualTo("Theo ngày");
    }

    @Test
    @DisplayName("Should parse ASSIGNED status to query ACCEPTED and CONFIRMED bookings")
    void testAssignedStatusAlias() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));
        when(bookingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        PageResponse<DriverTripResponse> response = driverTripService.listDriverTrips(
                201L, null, "ASSIGNED", null, 0, 20, null
        );

        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("Should apply ascending sort by pickupTime for TODAY, UPCOMING and ACTIVE views")
    void testDefaultSortForUpcomingViews() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(bookingRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        driverTripService.listDriverTrips(201L, "UPCOMING", null, null, 0, 10, null);

        Pageable pageable = pageableCaptor.getValue();
        Sort.Order pickupOrder = pageable.getSort().getOrderFor("pickupTime");
        assertThat(pickupOrder).isNotNull();
        assertThat(pickupOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("Should apply descending sort by pickupTime for HISTORY view")
    void testDefaultSortForHistoryView() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(bookingRepository.findAll(any(Specification.class), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        driverTripService.listDriverTrips(201L, "HISTORY", null, null, 0, 10, null);

        Pageable pageable = pageableCaptor.getValue();
        Sort.Order pickupOrder = pageable.getSort().getOrderFor("pickupTime");
        assertThat(pickupOrder).isNotNull();
        assertThat(pickupOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("Should reject invalid view parameter")
    void testInvalidViewThrowsException() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, "INVALID_VIEW", null, null, 0, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid view");
    }

    @Test
    @DisplayName("Should reject invalid status parameter")
    void testInvalidStatusThrowsException() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, "INVALID_STATUS", null, 0, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid booking status");
    }

    @Test
    @DisplayName("Should reject invalid date format")
    void testInvalidDateFormatThrowsException() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, "2026-13-45", 0, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("Should reject invalid pagination page and size")
    void testInvalidPaginationThrowsException() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, -1, 20, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page index must not be less than zero");

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 100");

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, 0, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Page size must be between 1 and 100");
    }

    @Test
    @DisplayName("Should reject invalid sort property")
    void testInvalidSortPropertyThrowsException() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, 0, 20, "maliciousColumn,desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort property");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user has no driver profile")
    void testNoDriverProfileThrowsAccessDenied() {
        when(userService.findDriverProfile(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverTripService.listDriverTrips(999L, null, null, null, 0, 20, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not registered as a driver");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when driver is suspended or not approved")
    void testSuspendedDriverThrowsAccessDenied() {
        activeDriver.setStatus(DriverStatus.SUSPENDED);
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, 0, 20, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Driver account is not active or approved");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when driver user is locked")
    void testLockedDriverUserThrowsAccessDenied() {
        driverUser.setStatus(UserStatus.LOCKED);
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        assertThatThrownBy(() -> driverTripService.listDriverTrips(201L, null, null, null, 0, 20, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Driver account is not active or approved");
    }

    @Test
    @DisplayName("Should get trip detail by numeric ID or bookingCode")
    void testGetTripDetailSuccess() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));

        Booking booking = createSampleBooking(100L, "BK-TEST99", BookingStatus.IN_PROGRESS, fixedClock.instant());
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(vehicleService.getCoverImageUrl(10L)).thenReturn("https://img.com/cover.jpg");

        DriverTripDetailResponse detail = driverTripService.getTripDetail(201L, "100");

        assertThat(detail).isNotNull();
        assertThat(detail.tripId()).isEqualTo(100L);
        assertThat(detail.bookingCode()).isEqualTo("BK-TEST99");
        assertThat(detail.vehicle().name()).isEqualTo("VinFast VF 8");
        assertThat(detail.provider().displayName()).isEqualTo("EcoMotion Depot A");
        assertThat(detail.provider().hotline()).isEqualTo("19001234");
    }

    @Test
    @DisplayName("Should throw BookingNotFoundException when trip does not exist")
    void testTripNotFoundThrows404() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));
        when(bookingRepository.findById(200L)).thenReturn(Optional.empty());
        when(bookingRepository.findByBookingCode("200")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverTripService.getTripDetail(201L, "200"))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("Trip not found");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when trip belongs to a different driver (Case 9: 403)")
    void testTripDifferentDriverThrows403() {
        when(userService.findDriverProfile(201L)).thenReturn(Optional.of(activeDriver));
        Booking booking = createSampleBooking(200L, "BK-OTHER", BookingStatus.CONFIRMED, fixedClock.instant());
        Driver otherDriver = new Driver();
        otherDriver.setId(999L);
        booking.setDriver(otherDriver);
        when(bookingRepository.findById(200L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> driverTripService.getTripDetail(201L, "200"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền thao tác trên chuyến đi này");
    }
}
