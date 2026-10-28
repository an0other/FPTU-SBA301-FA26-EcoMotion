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
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
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

    private static final int MAX_AUTO_REASSIGN_RETRIES = 3;
    private static final Duration ASSIGNMENT_TIMEOUT_DURATION = Duration.ofMinutes(15);
    private static final double SOLVER_MAX_TIME_SECONDS = 5.0;

    private static final List<BookingStatus> ACTIVE_DRIVER_STATUSES = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.IN_PROGRESS
    );

    static {
        try {
            Loader.loadNativeLibraries();
            log.info("Google OR-Tools native libraries loaded successfully");
        } catch (Throwable t) {
            log.warn("Failed to load Google OR-Tools native libraries: {}", t.getMessage());
        }
    }

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
                + " (Điểm tối ưu: " + String.format(Locale.ROOT, "%.1f", suggested.suitabilityScore())
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
    public Driver autoAssign(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
            log.warn("Booking {} is not in CONFIRMED/DRIVER_ASSIGNED status for auto assign: {}", bookingId, booking.getStatus());
            return null;
        }

        Long providerId = booking.getVehicle().getProvider().getId();
        AssignmentSuggestionResponse suggestion = suggestDriver(bookingId, providerId);

        if (suggestion.suggestedDriver() == null) {
            log.warn("OR-Tools solver could not find eligible driver for booking {}", booking.getBookingCode());
            return null;
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
            return null;
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ASSIGNMENT_TIMEOUT_DURATION);

        booking.setDriver(driver);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(now);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignmentExpiresAt(expiresAt);
        bookingRepository.saveAndFlush(booking);

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

        return driver;
    }

    @Override
    @Transactional
    public Driver manualAssign(Long bookingId, Long driverId, Long providerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        if (!booking.getVehicle().getProvider().getId().equals(providerId)) {
            throw new AccessDeniedException("Đơn thuê không thuộc quyền quản lý của nhà cung cấp");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
            throw new ConflictException("Chỉ có thể phân công tài xế khi đơn ở trạng thái Đã cọc (CONFIRMED) hoặc Phân công (DRIVER_ASSIGNED)");
        }

        // Concurrency protection: lock driver row (Rule 9)
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
        Instant expiresAt = now.plus(ASSIGNMENT_TIMEOUT_DURATION);

        booking.setDriver(driver);
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignedAt(now);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignmentExpiresAt(expiresAt);
        bookingRepository.saveAndFlush(booking);

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
        List<DriverAssignment> expiredAssignments = driverAssignmentRepository.findExpiredAssignments(now);

        for (DriverAssignment da : expiredAssignments) {
            log.info("Assignment {} for booking {} expired without response", da.getId(), da.getBooking().getBookingCode());
            da.setStatus(AssignmentStatus.TIMEOUT);
            da.setRespondedAt(now);
            driverAssignmentRepository.save(da);

            Booking booking = da.getBooking();
            if (booking.getStatus() == BookingStatus.DRIVER_ASSIGNED && booking.getDriver() != null
                    && booking.getDriver().getId().equals(da.getDriver().getId())) {
                triggerAutoReassign(booking.getId(), da.getDriver().getId(), "Tài xế không phản hồi trong thời hạn quy định (TIMEOUT)");
            }
            notificationStreamService.sendEventToProvider(booking.getVehicle().getProvider().getId(),
                    "DRIVER_STATUS_CHANGED", Map.of("driverId", da.getDriver().getId(),
                            "bookingCode", booking.getBookingCode()));
        }
    }

    @Override
    @Transactional
    public void triggerAutoReassign(Long bookingId, Long rejectedDriverId, String reason) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingId));

        int currentRetries = booking.getReassignCount() != null ? booking.getReassignCount() : 0;
        int nextRetries = currentRetries + 1;
        booking.setReassignCount(nextRetries);

        booking.setDriver(null);
        booking.setAssignedAt(null);
        booking.setAssignmentExpiresAt(null);

        if (nextRetries > MAX_AUTO_REASSIGN_RETRIES) {
            log.warn("Booking {} reached max reassign retries ({}/{}). Waiting for manual intervention.",
                    booking.getBookingCode(), currentRetries, MAX_AUTO_REASSIGN_RETRIES);
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.saveAndFlush(booking);
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.saveAndFlush(booking);

        log.info("Triggering auto reassign for booking {} (round {}/{}). Reason: {}",
                booking.getBookingCode(), nextRetries, MAX_AUTO_REASSIGN_RETRIES, reason);

        autoAssign(bookingId);
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
        if (driver.getLicenseExpiry() == null || driver.getLicenseExpiry().isBefore(pickupDate)) {
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
        double suitabilityScore = calculateSuitabilityScore(distanceAndEta.etaMinutes(), activeTripsToday, rating);

        String recommendationReason = buildRecommendationReason(distanceAndEta.etaMinutes(), activeTripsToday, rating);

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
        int n = eligibleCandidates.size();
        if (n == 0) {
            return new SolverOptimizationResult("INFEASIBLE", null);
        }
        if (n == 1) {
            return new SolverOptimizationResult("OPTIMAL", eligibleCandidates.getFirst());
        }

        try {
            CpModel model = new CpModel();
            BoolVar[] x = new BoolVar[n];
            for (int i = 0; i < n; i++) {
                x[i] = model.newBoolVar("driver_" + eligibleCandidates.get(i).driverId());
            }

            model.addExactlyOne(x);

            long[] penalties = new long[n];
            for (int i = 0; i < n; i++) {
                DriverCandidateResponse c = eligibleCandidates.get(i);
                int eta = c.etaMinutes() != null ? c.etaMinutes() : 30;
                long workload = c.activeTripsCount() != null ? c.activeTripsCount() : 0L;
                double rating = c.ratingAvg() != null ? c.ratingAvg().doubleValue() : 5.0;
                long ratingPenalty = Math.round((5.0 - rating) * 20.0);

                penalties[i] = (eta * 10L) + (workload * 50L) + (ratingPenalty * 5L);
            }

            LinearExpr objective = LinearExpr.weightedSum(x, penalties);
            model.minimize(objective);

            CpSolver solver = new CpSolver();
            solver.getParameters().setMaxTimeInSeconds(SOLVER_MAX_TIME_SECONDS);
            CpSolverStatus status = solver.solve(model);

            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                for (int i = 0; i < n; i++) {
                    if (solver.booleanValue(x[i])) {
                        return new SolverOptimizationResult(status.name(), eligibleCandidates.get(i));
                    }
                }
            }

            log.warn("OR-Tools solver finished with status: {}, falling back to highest score candidate", status);
            return new SolverOptimizationResult(status.name(), eligibleCandidates.getFirst());
        } catch (Throwable t) {
            log.error("Exception during CP-SAT solving, fallback to top heuristic candidate: {}", t.getMessage());
            return new SolverOptimizationResult("FALLBACK_HEURISTIC", eligibleCandidates.getFirst());
        }
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
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        int slotMinutes = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : (booking.getPlan() != null ? booking.getPlan().getSlotLengthMinutes() : 60);

        int quantity = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);

        return booking.getPickupTime().plus(Duration.ofMinutes((long) slotMinutes * Math.max(1, quantity)));
    }

    private MapGeocodingService.GeoPoint resolvePickupCoordinates(Booking booking) {
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
        return new MapGeocodingService.GeoPoint(10.7769, 106.7009, "TP. Hồ Chí Minh");
    }

    private DistanceAndEta calculateDistanceAndEta(
            Optional<VehicleLocation> latestLoc,
            MapGeocodingService.GeoPoint pickupPoint,
            Provider provider
    ) {
        double driverLat;
        double driverLon;

        if (latestLoc.isPresent()) {
            driverLat = latestLoc.get().getLatitude();
            driverLon = latestLoc.get().getLongitude();
        } else if (provider.getDepotLatitude() != null && provider.getDepotLongitude() != null) {
            driverLat = provider.getDepotLatitude().doubleValue();
            driverLon = provider.getDepotLongitude().doubleValue();
        } else {
            driverLat = 10.7769;
            driverLon = 106.7009;
        }

        double distanceKm = haversineDistanceKm(driverLat, driverLon, pickupPoint.latitude(), pickupPoint.longitude());
        int etaMinutes = Math.max(5, (int) Math.round((distanceKm / 30.0) * 60.0) + 5);

        return new DistanceAndEta(distanceKm, etaMinutes);
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
        double etaScore = Math.max(0.0, 100.0 - (etaMinutes * 1.5));
        double workloadScore = Math.max(0.0, 100.0 - (activeTripsToday * 25.0));
        double ratingScore = (rating / 5.0) * 100.0;

        return (etaScore * 0.4) + (workloadScore * 0.3) + (ratingScore * 0.3);
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
            sb.append("Chưa có chuyến trong ngày, đảm bảo thể lực. ");
        } else {
            sb.append("Đã chạy ").append(activeTripsToday).append(" chuyến hôm nay. ");
        }

        if (rating >= 4.8) {
            sb.append("Đánh giá xuất sắc (").append(rating).append(" sao).");
        } else {
            sb.append("Đánh giá tích cực (").append(rating).append(" sao).");
        }

        return sb.toString();
    }

    private record DistanceAndEta(Double distanceKm, Integer etaMinutes) {
    }

    private EvaluationContext evaluationContext(Booking booking, List<Driver> drivers) {
        List<Long> ids = drivers.stream().map(Driver::getId).toList();
        var availability = driverAvailabilityService.inspect(booking.getVehicle().getProvider().getId(),
                drivers, booking.getVehicle(), booking.getPickupTime(), calculateBookingEndTime(booking), booking.getId());
        Map<Long, Long> workloads = new java.util.HashMap<>();
        Map<Long, VehicleLocation> locations = new java.util.HashMap<>();
        List<Long> blocked = List.of();
        if (!ids.isEmpty()) {
            LocalDate today = LocalDate.now(clock.withZone(ClockConfig.VIETNAM_ZONE));
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
