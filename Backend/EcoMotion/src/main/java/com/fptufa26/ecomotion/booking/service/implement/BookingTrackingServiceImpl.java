package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.VehicleLocationRepository;
import com.fptufa26.ecomotion.booking.service.BookingTrackingService;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class BookingTrackingServiceImpl implements BookingTrackingService {

    private static final Duration STALE_LOCATION_THRESHOLD = Duration.ofSeconds(60);

    private final VehicleLocationRepository vehicleLocationRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final MapGeocodingService mapGeocodingService;
    private final Clock clock;

    @Override
    public TripTrackingResponse buildTrackingResponse(Booking booking) {
        TripLocationResponse location = getLatestLocation(booking.getId());
        Double pickupLat = booking.getPickupLatitude() == null ? null : booking.getPickupLatitude().doubleValue();
        Double pickupLon = booking.getPickupLongitude() == null ? null : booking.getPickupLongitude().doubleValue();
        if (pickupLat == null && pickupLon == null
                && booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()) {
            var point = mapGeocodingService.geocodeAddress(booking.getPickupAddress());
            if (point.isPresent()) {
                pickupLat = point.get().latitude();
                pickupLon = point.get().longitude();
            }
        }

        TripTrackingResponse.DriverSummary driverSummary = null;
        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driverSummary = new TripTrackingResponse.DriverSummary(
                    driver.getId(),
                    driver.getUser() == null ? null : driver.getUser().getFullName(),
                    driver.getUser() == null ? null : driver.getUser().getPhone(),
                    driver.getLicenseNo(),
                    driver.getPortraitUrl());
        }

        TripTrackingResponse.VehicleSummary vehicleSummary = null;
        if (booking.getVehicle() != null) {
            Vehicle vehicle = booking.getVehicle();
            BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
            String currentName = ((vehicle.getBrand() == null ? "" : vehicle.getBrand()) + " "
                    + (vehicle.getModel() == null ? "" : vehicle.getModel())).trim();
            String name = snapshot != null && snapshot.getVehicleName() != null
                    ? snapshot.getVehicleName() : currentName;
            String licensePlate = snapshot != null && snapshot.getVehicleLicensePlate() != null
                    ? snapshot.getVehicleLicensePlate() : vehicle.getLicensePlate();
            vehicleSummary = new TripTrackingResponse.VehicleSummary(
                    vehicle.getId(), name, vehicle.getBrand(), vehicle.getModel(), licensePlate);
        }

        return new TripTrackingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                DriverTripServiceImpl.deriveExecutionStatus(booking),
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getPickupTime(),
                booking.getPickupAddress(),
                pickupLat,
                pickupLon,
                location,
                driverSummary,
                vehicleSummary);
    }

    @Override
    public TripLocationResponse getLatestLocation(Long bookingId) {
        var now = clock.instant();
        return vehicleLocationRepository.findFirstByBooking_IdOrderByReceivedAtDesc(bookingId)
                .map(location -> {
                    boolean stale = Duration.between(location.getReceivedAt(), now)
                            .compareTo(STALE_LOCATION_THRESHOLD) > 0;
                    return new TripLocationResponse(
                            location.getLatitude(), location.getLongitude(), location.getAccuracyMeters(),
                            location.getRecordedAt(), location.getReceivedAt(), stale, stale ? "STALE" : "LIVE");
                })
                .orElse(new TripLocationResponse(null, null, null, null, null, false, "UNAVAILABLE"));
    }
}
