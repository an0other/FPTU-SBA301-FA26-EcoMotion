package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentAvailabilityResponse;
import com.fptufa26.ecomotion.booking.service.DriverAvailabilityService;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.entity.VehicleLocation;
import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.TripIncidentStatus;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.booking.repository.TripIncidentRepository;
import com.fptufa26.ecomotion.booking.repository.VehicleLocationRepository;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import com.fptufa26.ecomotion.common.config.ClockConfig;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import org.springframework.security.access.AccessDeniedException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptufa26.ecomotion.common.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;

import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DriverOptimizationServiceImpl implements DriverOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(DriverOptimizationServiceImpl.class);


    private static final List<BookingStatus> ACTIVE_DRIVER_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.AWAITING_START_CONFIRMATION,
            BookingStatus.AWAITING_END_CONFIRMATION,
            BookingStatus.IN_PROGRESS
    );

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final TripIncidentRepository tripIncidentRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final MapGeocodingService mapGeocodingService;
    private final NotificationStreamService notificationStreamService;
    private final Clock clock;
    private final com.fptufa26.ecomotion.booking.service.BookingNotificationService bookingNotificationService;
    private final com.fptufa26.ecomotion.booking.config.OptimizationProperties optimizationProperties;
    private final com.fptufa26.ecomotion.booking.service.DriverSelectionService driverSelectionService;
    private final DriverAvailabilityService driverAvailabilityService;

    @Override
    @Transactional(readOnly = true)
    public List<DriverCandidateResponse> evaluateCandidates(Long bookingId, Long providerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (!booking.getVehicle().getProvider().getId().equals(providerId)) {
            throw new AccessDeniedException("Đơn thuê không thuộc quyền quản lý của nhà cung cấp");
        }

        List<Driver> allDrivers = userService.findDriversByProviderId(providerId);
        List<Long> rejectedDriverIds = driverAssignmentRepository.findRejectedDriverIdsForBooking(bookingId);

        Instant bookingStart = booking.getPickupTime();
        Instant bookingEnd = calculateBookingEndTime(booking);

        MapGeocodingService.GeoPoint pickupPoint = resolvePickupCoordinates(booking);

        var context = evaluationContext(booking, allDrivers);
        List<DriverCandidateResponse> evaluatedList = new ArrayList<>();
        for (Driver driver : allDrivers) {
            DriverCandidateResponse response = evaluateDriver(
                    driver,
                    booking,
                    bookingStart,
                    bookingEnd,
                    pickupPoint,
                    rejectedDriverIds, context
            ).withAvailability(context.availability().get(driver.getId()));
            evaluatedList.add(response);
        }

        evaluatedList.sort(Comparator
                .comparing(DriverCandidateResponse::eligible).reversed()
                .thenComparing(DriverCandidateResponse::suitabilityScore, Comparator.reverseOrder())
                .thenComparing(DriverCandidateResponse::driverId)
        );

        if (!evaluatedList.isEmpty() && evaluatedList.getFirst().eligible()) {
            DriverCandidateResponse best = evaluatedList.getFirst();
            evaluatedList.set(0, new DriverCandidateResponse(
                    best.driverId(), best.userId(), best.fullName(), best.phone(),
                    best.licenseNo(), best.licenseClass(), best.licenseExpiry(),
                    best.portraitUrl(), best.ratingAvg(), best.ratingCount(),
                    best.etaMinutes(), best.distanceKm(), best.activeTripsCount(),
                    best.suitabilityScore(), best.eligible(), best.ineligibilityReason(),
                    best.recommendationReason(), true, best.availability()
            ));
        }

        return evaluatedList;
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentSuggestionResponse suggestDriver(Long bookingId, Long providerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        List<DriverCandidateResponse> candidates = evaluateCandidates(bookingId, providerId);
        List<DriverCandidateResponse> eligibleCandidates = candidates.stream()
                .filter(DriverCandidateResponse::eligible)
                .toList();

        if (eligibleCandidates.isEmpty()) {
            return new AssignmentSuggestionResponse(
                    booking.getBookingCode(),
                    booking.getAssignmentMode() != null ? booking.getAssignmentMode() : AssignmentMode.AUTO_SUGGEST,
                    "INFEASIBLE",
                    null,
                    candidates,
                    "Không có tài xế nào đủ điều kiện nhận chuyến tại thời điểm này (trùng lịch, GPLX hoặc sự cố)."
            );
        }

        SolverOptimizationResult solverResult = solveWithCpSat(eligibleCandidates);

        DriverCandidateResponse suggested = solverResult.selectedCandidate() != null
                ? solverResult.selectedCandidate()
                : eligibleCandidates.getFirst();

        String explanation = "Đề xuất tài xế " + suggested.fullName()
                + " (Điểm xếp hạng: " + String.format(Locale.ROOT, "%.1f", suggested.suitabilityScore())
                + ", ETA: " + suggested.etaMinutes() + " phút, Đánh giá: " + suggested.ratingAvg() + "*). "
                + suggested.recommendationReason();

        return new AssignmentSuggestionResponse(
                booking.getBookingCode(),
                booking.getAssignmentMode() != null ? booking.getAssignmentMode() : AssignmentMode.AUTO_SUGGEST,
                solverResult.status(),
                suggested,
                candidates,
                explanation
        );
    }

    @Override
    @Transactional
    public java.time.Instant assignmentDeadline() {
        return clock.instant().plus(Duration.ofMinutes(optimizationProperties.getAssignmentTimeoutMinutes()));
    }

    @Override
    @Transactional
    public Driver autoAssign(Long bookingId) {
        return autoAssignWithResult(bookingId).driver();
    }

    @Override
    @Transactional
    public AutoAssignment autoAssignWithResult(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
            log.warn("Booking {} is not in CONFIRMED/DRIVER_ASSIGNED status for auto assign: {}", bookingId, booking.getStatus());
            return new AutoAssignment(null, "INFEASIBLE");
        }

        if (booking.getStatus() == BookingStatus.DRIVER_ASSIGNED && booking.getDriver() != null
                && !booking.isDriverReplacementRequired() && booking.getAssignmentExpiresAt() != null && booking.getAssignmentExpiresAt().isAfter(clock.instant())) {
            return new AutoAssignment(booking.getDriver(), "ALREADY_ASSIGNED");
        }
        Long providerId = booking.getVehicle().getProvider().getId();
        AssignmentSuggestionResponse suggestion = suggestDriver(bookingId, providerId);

        if (suggestion.suggestedDriver() == null) {
            log.warn("OR-Tools solver could not find eligible driver for booking {}", booking.getBookingCode());
            bookingNotificationService.changed(booking, "DRIVER_UNAVAILABLE");
            return new AutoAssignment(null, "INFEASIBLE");
        }

        Long driverId = suggestion.suggestedDriver().driverId();
        // Concurrency protection: lock driver row
        Driver driver = userService.findDriverByIdForUpdate(driverId)
                .orElseThrow(() -> new IllegalStateException("Driver not found: " + driverId));

        DriverCandidateResponse validated = evaluateDriver(driver, booking, booking.getPickupTime(),
                calculateBookingEndTime(booking), resolvePickupCoordinates(booking),
                driverAssignmentRepository.findRejectedDriverIdsForBooking(bookingId),
                evaluationContext(booking, List.of(driver)));
        if (!validated.eligible()) {
            log.warn("Driver {} became ineligible: {}", driverId, validated.ineligibilityReason());
            return new AutoAssignment(null, "INFEASIBLE");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(optimizationProperties.getAssignmentTimeoutMinutes()));

        if (booking.isDriverReplacementRequired()) {
            booking.setDriverReplacementRequired(false);
            booking.setDriverIssueResolvedAt(now);
        }
        booking.setDriver(driver);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(now);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignmentExpiresAt(expiresAt);
        bookingRepository.saveAndFlush(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");

        recallActiveAssignments(booking.getId());
        DriverAssignment assignment = new DriverAssignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setStatus(AssignmentStatus.PENDING_RESPONSE);
        assignment.setAssignedAt(now);
        assignment.setExpiresAt(expiresAt);
        assignment.setRoundNumber(booking.getReassignCount() != null ? booking.getReassignCount() + 1 : 1);
        driverAssignmentRepository.save(assignment);

        driver.setStatusUpdatedAt(now);
        userService.saveDriver(driver);

        notificationStreamService.sendEventToUser(driver.getUser().getId(), "DRIVER_ASSIGNED", Map.of(
                "bookingCode", booking.getBookingCode(),
                "pickupTime", booking.getPickupTime().toString(),
                "assignedAt", now.toString()
        ));
        notificationStreamService.sendEventToProvider(providerId, "DRIVER_ASSIGNED", Map.of(
                "bookingCode", booking.getBookingCode(),
                "driverId", driver.getId(),
                "assignedAt", now.toString()
        ));

        log.info("Auto assigned driver {} to booking {} via OR-Tools. Expires at: {}",
                driver.getId(), booking.getBookingCode(), expiresAt);

        return new AutoAssignment(driver, suggestion.solverStatus());
    }

    @Override
    @Transactional
    public Driver manualAssign(Long bookingId, Long driverId, Long providerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (!booking.getVehicle().getProvider().getId().equals(providerId)) {
            throw new AccessDeniedException("Đơn thuê không thuộc quyền quản lý của nhà cung cấp");
        }

        if (booking.getDriver() != null && booking.getDriver().getId().equals(driverId)
                && booking.getStatus() == BookingStatus.DRIVER_ASSIGNED && !booking.isDriverReplacementRequired()
                && booking.getAssignmentExpiresAt() != null && booking.getAssignmentExpiresAt().isAfter(clock.instant())) return booking.getDriver();
        boolean replacement = booking.isDriverReplacementRequired()
                && java.util.Set.of(BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED).contains(booking.getStatus());
        if (booking.getStatus() != BookingStatus.CONFIRMED && !replacement)
            throw new ConflictException("Chỉ có thể gán khi đã cọc hoặc đang yêu cầu tài xế thay thế");

        Driver driver = userService.findDriverByIdForUpdate(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Tài xế không tồn tại hoặc không thuộc nhà cung cấp"));

        if (!driver.getProvider().getId().equals(providerId)) {
            throw new AccessDeniedException("Tài xế không thuộc nhà cung cấp");
        }

        DriverCandidateResponse validated = evaluateDriver(driver, booking, booking.getPickupTime(),
                calculateBookingEndTime(booking), resolvePickupCoordinates(booking),
                driverAssignmentRepository.findRejectedDriverIdsForBooking(bookingId),
                evaluationContext(booking, List.of(driver)));
        if (!validated.eligible()) {
            throw new ConflictException(validated.ineligibilityReason());
        }

        Instant now = clock.instant();
        if (booking.getAssignedAt() != null && now.isBefore(booking.getAssignedAt().plusMillis(1))) now = booking.getAssignedAt().plusMillis(1);
        Instant expiresAt = now.plus(Duration.ofMinutes(optimizationProperties.getAssignmentTimeoutMinutes()));

        booking.setDriver(driver);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(now);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignmentExpiresAt(expiresAt);
        bookingRepository.saveAndFlush(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");

        recallActiveAssignments(booking.getId());
        DriverAssignment assignment = new DriverAssignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setStatus(AssignmentStatus.PENDING_RESPONSE);
        assignment.setAssignedAt(now);
        assignment.setExpiresAt(expiresAt);
        assignment.setRoundNumber(booking.getReassignCount() != null ? booking.getReassignCount() + 1 : 1);
        driverAssignmentRepository.save(assignment);

        driver.setStatusUpdatedAt(now);
        userService.saveDriver(driver);

        notificationStreamService.sendEventToUser(driver.getUser().getId(), "DRIVER_ASSIGNED", Map.of(
                "bookingCode", booking.getBookingCode(),
                "pickupTime", booking.getPickupTime().toString(),
                "assignedAt", now.toString()
        ));
        notificationStreamService.sendEventToProvider(providerId, "DRIVER_ASSIGNED", Map.of(
                "bookingCode", booking.getBookingCode(),
                "driverId", driver.getId(),
                "assignedAt", now.toString()
        ));

        log.info("Manual assigned driver {} to booking {} by provider {}",
                driver.getId(), booking.getBookingCode(), providerId);

        return driver;
    }

    @Override
    @Transactional
    public void processPendingAssignmentTimeouts() {
        Instant now = clock.instant();
        for (Long bookingId : driverAssignmentRepository.findExpiredBookingIds(now)) {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
            if (booking == null) continue;
            for (DriverAssignment da : driverAssignmentRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId)) {
                if (da.getStatus() != AssignmentStatus.PENDING_RESPONSE) continue;
                if (!matchesAssignment(booking, da) || booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
                    da.setStatus(AssignmentStatus.RECALLED);
                    da.setRespondedAt(now);
                    driverAssignmentRepository.save(da);
                    continue;
                }
                DriverAssignment current = requireCurrentAssignment(booking, da.getDriver().getId());
                if (!java.util.Objects.equals(current.getId(), da.getId())) {
                    da.setStatus(AssignmentStatus.RECALLED);
                    da.setRespondedAt(now);
                    driverAssignmentRepository.save(da);
                    continue;
                }
                if (da.getExpiresAt() == null || da.getExpiresAt().isAfter(now)) continue;
                da.setStatus(AssignmentStatus.TIMEOUT);
                da.setRespondedAt(now);
                driverAssignmentRepository.saveAndFlush(da);
                triggerAutoReassign(bookingId, da.getDriver().getId(), "TIMEOUT");
                break;
            }
        }
    }

    @Override
    @Transactional
    public void recallActiveAssignments(Long bookingId) {
        driverAssignmentRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PENDING_RESPONSE || a.getStatus() == AssignmentStatus.ACCEPTED)
                .forEach(a -> {
                    a.setStatus(AssignmentStatus.RECALLED);
                    a.setRespondedAt(clock.instant());
                    driverAssignmentRepository.save(a);
                    notificationStreamService.sendEventToUser(a.getDriver().getUser().getId(), "DRIVER_REASSIGNED", java.util.Map.of("bookingCode", a.getBooking().getBookingCode()));
                });
        driverAssignmentRepository.flush();
    }

    @Override
    public DriverAssignment requireCurrentAssignment(Booking booking, Long driverId) {
        return driverAssignmentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.PENDING_RESPONSE)
                .filter(a -> a.getDriver().getId().equals(driverId) && matchesAssignment(booking, a))
                .max(Comparator.comparing(DriverAssignment::getId))
                .orElseThrow(() -> new ConflictException("Phân công đã được thay thế. Vui lòng tải lại lịch trình"));
    }

    private boolean matchesAssignment(Booking booking, DriverAssignment assignment) {
        return booking.getDriver() != null
                && booking.getDriver().getId().equals(assignment.getDriver().getId())
                && java.util.Objects.equals(booking.getAssignedAt(), assignment.getAssignedAt())
                && java.util.Objects.equals(booking.getAssignmentExpiresAt(), assignment.getExpiresAt());
    }

    @Override
    @Transactional
    public void triggerAutoReassign(Long bookingId, Long rejectedDriverId, String reason) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (!java.util.Set.of(BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED).contains(booking.getStatus())
                || booking.getDriver() == null || !booking.getDriver().getId().equals(rejectedDriverId)) return;
        recallActiveAssignments(bookingId);
        int currentRetries = booking.getReassignCount() != null ? booking.getReassignCount() : 0;
        int nextRetries = currentRetries + 1;
        booking.setReassignCount(nextRetries);

        booking.setDriver(null);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignedAt(null);
        booking.setAssignmentExpiresAt(null);

        if (nextRetries > optimizationProperties.getMaxReassignRetries()) {
            log.warn("Booking {} reached max reassign retries ({}/{}). Waiting for manual intervention.",
                    booking.getBookingCode(), currentRetries, optimizationProperties.getMaxReassignRetries());
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.saveAndFlush(booking);
            bookingNotificationService.changed(booking, "DRIVER_UNAVAILABLE");
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.saveAndFlush(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");

        log.info("Triggering auto reassign for booking {} (round {}/{}). Reason: {}",
                booking.getBookingCode(), nextRetries, optimizationProperties.getMaxReassignRetries(), reason);

        Long providerId = booking.getVehicle().getProvider().getId();
        var suggestion = suggestDriver(bookingId, providerId);
        if (suggestion.suggestedDriver() != null) {
            Long selected = suggestion.suggestedDriver().driverId();
            java.util.stream.Stream.of(rejectedDriverId, selected).distinct().sorted()
                    .forEach(id -> userService.findDriverByIdForUpdate(id)
                            .orElseThrow(() -> new ConflictException("Không tìm thấy tài xế")));
            try {
                manualAssign(bookingId, selected, providerId);
            } catch (ConflictException exception) {
                log.warn("Reassignment candidate became unavailable for booking {}: {}", bookingId, exception.getMessage());
                bookingNotificationService.changed(booking, "DRIVER_UNAVAILABLE");
            }
        } else {
            bookingNotificationService.changed(booking, "DRIVER_UNAVAILABLE");
        }
    }

    private DriverCandidateResponse evaluateDriver(
            Driver driver,
            Booking booking,
            Instant bookingStart,
            Instant bookingEnd,
            MapGeocodingService.GeoPoint pickupPoint,
            List<Long> rejectedDriverIds,
            EvaluationContext context
    ) {
        Long driverId = driver.getId();
        String fullName = driver.getUser() != null ? driver.getUser().getFullName() : "Tài xế #" + driverId;
        String phone = driver.getUser() != null ? driver.getUser().getPhone() : null;

        if (booking.isDriverReplacementRequired() && booking.getDriver() != null
                && booking.getDriver().getId().equals(driverId)) {
            return buildIneligible(driver, fullName, phone, "Tài xế đã báo không thể thực hiện chuyến, cần tài xế thay thế");
        }
        if (rejectedDriverIds.contains(driverId)) {
            return buildIneligible(driver, fullName, phone, "Tài xế đã từ chối booking này trong vòng phân công hiện tại");
        }

        if (driver.getStatus() != DriverStatus.APPROVED) {
            return buildIneligible(driver, fullName, phone, "Hồ sơ tài xế chưa được duyệt hoặc đang bị khóa (Trạng thái: " + driver.getStatus() + ")");
        }

        if (driver.getUser() == null || driver.getUser().getStatus() != UserStatus.ACTIVE) {
            return buildIneligible(driver, fullName, phone, "Tài khoản người dùng của tài xế không ở trạng thái hoạt động (ACTIVE)");
        }

        if (driver.getActivityStatus() == DriverActivityStatus.OFFLINE) {
            return buildIneligible(driver, fullName, phone, "Tài xế đang ngoại tuyến (OFFLINE)");
        }

        if (driver.getActivityStatus() == DriverActivityStatus.UNAVAILABLE) {
            return buildIneligible(driver, fullName, phone, "Tài xế đang tạm nghỉ (UNAVAILABLE)");
        }

        LocalDate pickupDate = LocalDate.ofInstant(bookingStart, ClockConfig.VIETNAM_ZONE);
        if (driver.getLicenseExpiry() == null || driver.getLicenseExpiry().isBefore(LocalDate.ofInstant(bookingEnd.minusNanos(1), ClockConfig.VIETNAM_ZONE))) {
            return buildIneligible(driver, fullName, phone, "GPLX hết hạn vào ngày đón (" + driver.getLicenseExpiry() + " < " + pickupDate + ")");
        }

        Vehicle vehicle = booking.getVehicle();
        if (!isLicenseClassCompatible(driver.getLicenseClass(), vehicle.getSeatCount(), vehicle.getTransmission())) {
            return buildIneligible(driver, fullName, phone, "Hạng GPLX " + driver.getLicenseClass()
                    + " không tương thích xe " + vehicle.getSeatCount() + " chỗ, hộp số " + vehicle.getTransmission());
        }

        if (context.blockedDrivers().contains(driverId)) {
            return buildIneligible(driver, fullName, phone, "Tài xế đang có sự cố chưa xử lý (OPEN / INVESTIGATING)");
        }

        var availability = context.availability().get(driverId);
        if (availability.reason() != null) {
            return buildIneligible(driver, fullName, phone, availability.reason()
                    + (availability.conflictingBookingCode() == null ? "" : ": " + availability.conflictingBookingCode()));
        }
        long activeTripsToday = context.workloads().getOrDefault(driverId, 0L);

        DistanceAndEta distanceAndEta = calculateDistanceAndEta(
                Optional.ofNullable(context.locations().get(driverId)), pickupPoint, booking.getVehicle().getProvider());

        double rating = driver.getRatingAvg() != null ? driver.getRatingAvg().doubleValue() : 5.0;
        double suitabilityScore = calculateSuitabilityScore(distanceAndEta.etaMinutes() == null ? optimizationProperties.getUnknownEtaMinutes() : distanceAndEta.etaMinutes(), activeTripsToday, rating);

        String recommendationReason = (distanceAndEta.etaMinutes() == null ? "Thiếu điểm đón hoặc vị trí mới/depot; ETA chưa xác định. "
                : "Nguồn vị trí: " + distanceAndEta.source() + "; ETA ước tính, không có dữ liệu giao thông. ")
                + (distanceAndEta.etaMinutes() == null ? "Không dùng tọa độ mặc định làm GPS tài xế."
                    : buildRecommendationReason(distanceAndEta.etaMinutes(), activeTripsToday, rating));

        return new DriverCandidateResponse(
                driverId,
                driver.getUser() != null ? driver.getUser().getId() : null,
                fullName,
                phone,
                driver.getLicenseNo(),
                driver.getLicenseClass(),
                driver.getLicenseExpiry(),
                driver.getPortraitUrl(),
                driver.getRatingAvg(),
                driver.getRatingCount(),
                distanceAndEta.etaMinutes(),
                distanceAndEta.distanceKm(),
                activeTripsToday,
                suitabilityScore,
                true,
                null,
                recommendationReason,
                false
        );
    }

    private DriverCandidateResponse buildIneligible(Driver driver, String fullName, String phone, String reason) {
        return new DriverCandidateResponse(
                driver.getId(),
                driver.getUser() != null ? driver.getUser().getId() : null,
                fullName,
                phone,
                driver.getLicenseNo(),
                driver.getLicenseClass(),
                driver.getLicenseExpiry(),
                driver.getPortraitUrl(),
                driver.getRatingAvg(),
                driver.getRatingCount(),
                null,
                null,
                0L,
                0.0,
                false,
                reason,
                null,
                false
        );
    }

    private SolverOptimizationResult solveWithCpSat(List<DriverCandidateResponse> eligibleCandidates) {
        var result = driverSelectionService.select(eligibleCandidates);
        return new SolverOptimizationResult(result.status(), result.candidate());
    }

    private boolean isLicenseClassCompatible(String licenseClass, int seatCount, Transmission transmission) {
        if (licenseClass == null || licenseClass.isBlank()) {
            return false;
        }
        String cls = licenseClass.trim().toUpperCase(Locale.ROOT);

        if (transmission == Transmission.MANUAL && "B1".equals(cls)) {
            return false;
        }

        if (seatCount <= 9) {
            return Set.of("B", "B1", "B2", "C", "D", "E").contains(cls);
        } else if (seatCount <= 30) {
            return Set.of("D", "E").contains(cls);
        } else {
            return "E".equals(cls);
        }
    }

    private Instant calculateBookingEndTime(Booking booking) {
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new ConflictException("Thiếu chính sách thời lượng đã chốt; chưa thể phân công"));
        Integer slotMinutes = snapshot.getSlotLengthMinutes();
        Integer quantity = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(null);
        if (quantity == null && snapshot.getSlotPrice() != null && snapshot.getSlotPrice().signum() > 0
                && booking.getTotalAmount() != null) {
            var division = booking.getTotalAmount().divideAndRemainder(snapshot.getSlotPrice());
            if (division[1].signum() == 0) quantity = division[0].intValueExact();
        }
        if (booking.getPickupTime() == null || slotMinutes == null || slotMinutes <= 0 || quantity == null || quantity <= 0)
            throw new ConflictException("Không xác định được khoảng thời gian thuê đáng tin cậy; chưa thể phân công");
        return booking.getPickupTime().plus(Duration.ofMinutes((long) slotMinutes * quantity));
    }

    private MapGeocodingService.GeoPoint resolvePickupCoordinates(Booking booking) {
        if (booking.getPickupLatitude() != null && booking.getPickupLongitude() != null) {
            return new MapGeocodingService.GeoPoint(booking.getPickupLatitude().doubleValue(),
                    booking.getPickupLongitude().doubleValue(), booking.getPickupAddress());
        }
        if (booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()) {
            Optional<MapGeocodingService.GeoPoint> geoOpt = mapGeocodingService.geocodeAddress(booking.getPickupAddress());
            if (geoOpt.isPresent()) {
                return geoOpt.get();
            }
        }
        Provider provider = booking.getVehicle().getProvider();
        if (provider.getDepotLatitude() != null && provider.getDepotLongitude() != null) {
            return new MapGeocodingService.GeoPoint(
                    provider.getDepotLatitude().doubleValue(),
                    provider.getDepotLongitude().doubleValue(),
                    provider.getDepotAddress()
            );
        }
        return null;
    }

    private DistanceAndEta calculateDistanceAndEta(
            Optional<VehicleLocation> latestLoc,
            MapGeocodingService.GeoPoint pickupPoint,
            Provider provider
    ) {
        double driverLat;
        double driverLon;
        String source;

        if (latestLoc.isPresent() && isFresh(latestLoc.get().getReceivedAt()) && isFresh(latestLoc.get().getRecordedAt())) {
            driverLat = latestLoc.get().getLatitude();
            driverLon = latestLoc.get().getLongitude();
            source = "GPS mới";
        } else if (provider.getDepotLatitude() != null && provider.getDepotLongitude() != null) {
            driverLat = provider.getDepotLatitude().doubleValue();
            driverLon = provider.getDepotLongitude().doubleValue();
            source = "bãi xe nhà cung cấp (dự phòng)";
        } else {
            return new DistanceAndEta(null, null, "không xác định");
        }

        if (pickupPoint == null) return new DistanceAndEta(null, null, "không xác định");
        double distanceKm = haversineDistanceKm(driverLat, driverLon, pickupPoint.latitude(), pickupPoint.longitude());
        int etaMinutes = Math.max(5, (int) Math.round((distanceKm / 30.0) * 60.0) + 5);

        return new DistanceAndEta(distanceKm, etaMinutes, source);
    }

    private boolean isFresh(Instant timestamp) {
        return timestamp != null && !timestamp.isAfter(clock.instant())
                && Duration.between(timestamp, clock.instant()).getSeconds() <= optimizationProperties.getLocationMaxAgeSeconds();
    }

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return r * c;
    }

    private double calculateSuitabilityScore(int etaMinutes, long activeTripsToday, double rating) {
        return -((long) etaMinutes * optimizationProperties.getWeightEta()
                + activeTripsToday * optimizationProperties.getWeightWorkload()
                + Math.round((5 - rating) * 20) * optimizationProperties.getWeightRating());
    }

    private String buildRecommendationReason(int etaMinutes, long activeTripsToday, double rating) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ưu thế: ");
        if (etaMinutes <= 15) {
            sb.append("Rất gần điểm đón (khoảng ").append(etaMinutes).append(" phút). ");
        } else {
            sb.append("Thời gian tiếp cận dự kiến ").append(etaMinutes).append(" phút. ");
        }

        if (activeTripsToday == 0) {
            sb.append("Chưa có chuyến giữ lịch trong ngày đón. ");
        } else {
            sb.append("Đã chạy ").append(activeTripsToday).append(" chuyến trong ngày đón. ");
        }

        if (rating >= 4.8) {
            sb.append("Đánh giá xuất sắc (").append(rating).append(" sao).");
        } else {
            sb.append("Đánh giá tích cực (").append(rating).append(" sao).");
        }

        return sb.toString();
    }

    private record DistanceAndEta(Double distanceKm, Integer etaMinutes, String source) {
    }

    private EvaluationContext evaluationContext(Booking booking, List<Driver> drivers) {
        List<Long> ids = drivers.stream().map(Driver::getId).toList();
        var availability = driverAvailabilityService.inspect(booking.getVehicle().getProvider().getId(),
                drivers, booking.getVehicle(), booking.getPickupTime(), calculateBookingEndTime(booking), booking.getId());
        Map<Long, Long> workloads = new java.util.HashMap<>();
        Map<Long, VehicleLocation> locations = new java.util.HashMap<>();
        List<Long> blocked = List.of();
        if (!ids.isEmpty()) {
            LocalDate today = LocalDate.ofInstant(booking.getPickupTime(), ClockConfig.VIETNAM_ZONE);
            bookingRepository.countTripsForDrivers(ids, ACTIVE_DRIVER_STATUSES,
                    today.atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant(),
                    today.plusDays(1).atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant())
                    .forEach(row -> workloads.put(row.getDriverId(), row.getTripCount()));
            vehicleLocationRepository.findLatestForDrivers(ids).forEach(location -> locations.put(location.getDriver().getId(), location));
            blocked = tripIncidentRepository.findBlockedDriverIds(ids, List.of(TripIncidentStatus.OPEN, TripIncidentStatus.INVESTIGATING));
        }
        return new EvaluationContext(availability, workloads, locations, blocked);
    }

    private record EvaluationContext(
            Map<Long, AssignmentAvailabilityResponse> availability,
            Map<Long, Long> workloads, Map<Long, VehicleLocation> locations, List<Long> blockedDrivers) {
    }

    private record SolverOptimizationResult(String status, DriverCandidateResponse selectedCandidate) {
    }
}
