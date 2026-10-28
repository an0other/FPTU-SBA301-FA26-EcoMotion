package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.dto.response.DriverStatusResponse;
import com.fptufa26.ecomotion.user.dto.response.DriverTripSummaryResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.service.DriverStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverStatusServiceImpl implements DriverStatusService {

    private static final List<BookingStatus> UPCOMING_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED
    );

    private final DriverRepository driverRepository;
    private final BookingQueryService bookingQueryService;
    private final NotificationStreamService notificationStreamService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DriverOperationalStatus calculateOperationalStatus(Driver driver) {
        if (driver == null) {
            return DriverOperationalStatus.OFFLINE;
        }

        if (driver.getStatus() != DriverStatus.APPROVED) {
            return DriverOperationalStatus.OFFLINE;
        }

        // 1. Kiểm tra xem tài xế có chuyến đang chạy không (ON_TRIP)
        boolean hasActiveTrip = bookingQueryService.existsByDriverIdAndStatus(driver.getId(), BookingStatus.IN_PROGRESS);
        if (hasActiveTrip) {
            return DriverOperationalStatus.ON_TRIP;
        }

        // 2. Kiểm tra trạng thái sẵn sàng chủ động của tài xế
        DriverActivityStatus activity = driver.getActivityStatus() != null
                ? driver.getActivityStatus()
                : DriverActivityStatus.AVAILABLE;

        if (activity == DriverActivityStatus.OFFLINE) {
            return DriverOperationalStatus.OFFLINE;
        }
        if (activity == DriverActivityStatus.UNAVAILABLE) {
            return DriverOperationalStatus.UNAVAILABLE;
        }

        // 3. Nếu activity là AVAILABLE: kiểm tra xem có lịch phân công tương lai không (SCHEDULED)
        Instant now = clock.instant();
        List<Booking> upcoming = bookingQueryService.findUpcomingDriverBookings(driver.getId(), UPCOMING_STATUSES, now);
        if (!upcoming.isEmpty()) {
            return DriverOperationalStatus.SCHEDULED;
        }

        return DriverOperationalStatus.AVAILABLE;
    }

    @Override
    @Transactional(readOnly = true)
    public DriverStatusResponse getDriverStatus(Long currentUserId) {
        Driver driver = driverRepository.findByUser_Id(currentUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế của tài khoản này"));

        return buildResponse(driver);
    }

    @Override
    @Transactional
    public DriverStatusResponse updateActivityStatus(Long currentUserId, DriverActivityStatus newStatus) {
        Driver driver = driverRepository.findByUserIdForUpdate(currentUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ tài xế của tài khoản này"));

        DriverOperationalStatus currentOpStatus = calculateOperationalStatus(driver);

        // Business Rule 14: Driver đang ON_TRIP không thể tự ép trạng thái thành AVAILABLE hay thay đổi nhận chuyến
        if (currentOpStatus == DriverOperationalStatus.ON_TRIP) {
            throw new ConflictException("Tài xế đang thực hiện chuyến đi (ON_TRIP), không thể tự thay đổi trạng thái sẵn sàng");
        }

        Instant now = clock.instant();
        driver.setActivityStatus(newStatus != null ? newStatus : DriverActivityStatus.AVAILABLE);
        driver.setStatusUpdatedAt(now);
        Driver saved = driverRepository.save(driver);

        log.info("Driver {} updated activity status to {} at {}", saved.getId(), newStatus, now);

        DriverStatusResponse response = buildResponse(saved);

        // Realtime notification sync cho cả Driver và Provider
        if (saved.getProvider() != null) {
            notificationStreamService.sendEventToProvider(
                    saved.getProvider().getId(),
                    "DRIVER_STATUS_CHANGED",
                    Map.of(
                            "driverId", saved.getId(),
                            "activityStatus", saved.getActivityStatus().name(),
                            "operationalStatus", response.operationalStatus().name(),
                            "statusUpdatedAt", now.toString()
                    )
            );
        }
        notificationStreamService.sendEventToUser(
                currentUserId,
                "DRIVER_STATUS_CHANGED",
                response
        );

        return response;
    }

    @Override
    @Transactional
    public void updateStatusTimestamp(Long driverId) {
        if (driverId == null) return;
        driverRepository.findByIdForUpdate(driverId).ifPresent(driver -> {
            Instant now = clock.instant();
            driver.setStatusUpdatedAt(now);
            driverRepository.save(driver);

            notificationStreamService.sendEventToUser(driver.getUser().getId(), "DRIVER_STATUS_CHANGED", buildResponse(driver));
            DriverOperationalStatus opStatus = calculateOperationalStatus(driver);
            if (driver.getProvider() != null) {
                notificationStreamService.sendEventToProvider(
                        driver.getProvider().getId(),
                        "DRIVER_STATUS_CHANGED",
                        Map.of(
                                "driverId", driver.getId(),
                                "activityStatus", driver.getActivityStatus() != null ? driver.getActivityStatus().name() : "AVAILABLE",
                                "operationalStatus", opStatus.name(),
                                "statusUpdatedAt", now.toString()
                        )
                );
            }
        });
    }

    private DriverStatusResponse buildResponse(Driver driver) {
        DriverOperationalStatus opStatus = calculateOperationalStatus(driver);
        Instant now = clock.instant();

        DriverTripSummaryResponse activeTrip = null;
        if (opStatus == DriverOperationalStatus.ON_TRIP) {
            activeTrip = bookingQueryService.findFirstByDriverIdAndStatus(driver.getId(), BookingStatus.IN_PROGRESS)
                    .map(this::toTripSummary)
                    .orElse(null);
        }

        DriverTripSummaryResponse nextTrip = null;
        List<Booking> upcoming = bookingQueryService.findUpcomingDriverBookings(driver.getId(), UPCOMING_STATUSES, now);
        if (!upcoming.isEmpty()) {
            nextTrip = toTripSummary(upcoming.getFirst());
        }

        String fullName = driver.getUser() != null ? driver.getUser().getFullName() : "";

        return new DriverStatusResponse(
                driver.getId(),
                driver.getUser() != null ? driver.getUser().getId() : null,
                fullName,
                driver.getActivityStatus() != null ? driver.getActivityStatus() : DriverActivityStatus.AVAILABLE,
                opStatus,
                driver.getStatusUpdatedAt() != null ? driver.getStatusUpdatedAt() : driver.getCreatedAt(),
                activeTrip,
                nextTrip
        );
    }

    private DriverTripSummaryResponse toTripSummary(Booking b) {
        String vehicleName = b.getVehicle() != null
                ? (b.getVehicle().getBrand() + " " + b.getVehicle().getModel())
                : "";
        return new DriverTripSummaryResponse(
                b.getBookingCode(),
                vehicleName,
                b.getPickupAddress(),
                b.getPickupTime(),
                b.getStatus() != null ? b.getStatus().name() : ""
        );
    }
}
