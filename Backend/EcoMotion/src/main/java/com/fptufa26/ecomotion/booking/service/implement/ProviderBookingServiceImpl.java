package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest;
import com.fptufa26.ecomotion.booking.dto.response.OperationalResolutionResponse;
import com.fptufa26.ecomotion.booking.dto.response.AvailableDriverResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.SettlementResultResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import com.fptufa26.ecomotion.booking.enums.DriverAssignmentMode;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.booking.service.DriverAvailabilityService;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.booking.service.BookingTrackingService;
import com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService;
import com.fptufa26.ecomotion.booking.specification.BookingSpecification;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.booking.dto.request.ResolveIncidentRequest;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.entity.TripIncident;
import com.fptufa26.ecomotion.booking.repository.TripEvidenceRepository;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.booking.service.TripIncidentService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import com.fptufa26.ecomotion.user.service.DriverStatusService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripIncidentResponse;
import com.fptufa26.ecomotion.booking.entity.DriverAssignment;
import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.booking.repository.TripIncidentRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import java.time.LocalDate;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderBookingServiceImpl implements ProviderBookingService {
    private final com.fptufa26.ecomotion.booking.service.BookingPaymentStateService paymentStates;

    private static final Logger log = LoggerFactory.getLogger(ProviderBookingServiceImpl.class);

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final com.fptufa26.ecomotion.finance.service.implement.RefundEventService refundEventService;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DriverAssignmentService driverAssignmentService;
    private final DriverAvailabilityService driverAvailabilityService;
    private final BookingTrackingService bookingTrackingService;
    private final VehicleOperationalRiskService vehicleOperationalRiskService;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final PaymentRequestService paymentRequestService;
    private final DisputeService disputeService;
    private final ScheduleService scheduleService;
    private final DriverOptimizationService driverOptimizationService;
    private final TripIncidentRepository tripIncidentRepository;
    private final TripEvidenceRepository tripEvidenceRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final TripIncidentService tripIncidentService;
    private final DriverStatusService driverStatusService;
    private final Clock clock;
    private final com.fptufa26.ecomotion.booking.service.BookingNotificationService bookingNotificationService;

    @Override
    public List<ProviderBookingResponse> listBookings(Long currentUserId, String status) {
        Provider provider = resolveProvider(currentUserId);
        BookingStatus filter = parseStatus(status);

        Specification<Booking> spec = BookingSpecification.forProviderQueue(provider.getId(), filter);
        List<Booking> bookings = bookingRepository.findAll(spec, DEFAULT_SORT);

        return toResponses(bookings);
    }

    @Override
    public Page<ProviderBookingResponse> listBookings(Long currentUserId, String status, Pageable pageable) {
        Provider provider = resolveProvider(currentUserId);
        BookingStatus filter = parseStatus(status);

        Specification<Booking> spec = BookingSpecification.forProviderQueue(provider.getId(), filter);
        Pageable effectivePageable = applyDefaultSortIfUnsorted(pageable);
        Page<Booking> page = bookingRepository.findAll(spec, effectivePageable);

        Map<Long, String> coverUrls = getCoverUrlsForBookings(page.getContent());
        List<Long> bookingIds = page.getContent().stream().map(Booking::getId).toList();
        Map<Long, List<PaymentTransaction>> txMap = bookingIds.isEmpty() ? Collections.emptyMap()
                : paymentTransactionRepository.findByBooking_IdIn(bookingIds).stream()
                        .collect(Collectors.groupingBy(tx -> tx.getBooking().getId()));
        return page.map(booking -> toResponse(booking, coverUrls.get(booking.getVehicle().getId()), txMap.getOrDefault(booking.getId(), List.of())));
    }

    @Override
    public PageResponse<ProviderBookingResponse> listBookingsPaged(Long currentUserId, String status, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        int pageIndex = safeOffset / safeLimit;

        Pageable pageable = PageRequest.of(pageIndex, safeLimit, DEFAULT_SORT);
        Page<ProviderBookingResponse> page = listBookings(currentUserId, status, pageable);

        boolean hasMore = (long) (pageIndex + 1) * safeLimit < page.getTotalElements();
        return new PageResponse<>(page.getContent(), page.getTotalElements(), safeOffset, safeLimit, hasMore);
    }

    @Override
    public ProviderBookingSummaryResponse getSummary(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        Long providerId = provider.getId();

        long total = bookingRepository.countByVehicle_Provider_Id(providerId);
        long pending = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.PENDING);
        long awaitingDeposit = bookingRepository.countByVehicle_Provider_IdAndStatus(
                providerId, BookingStatus.AWAITING_DEPOSIT);
        long accepted = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.ACCEPTED);
        long confirmed = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.CONFIRMED);
        long inProgress = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.IN_PROGRESS);
        long completed = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.COMPLETED);
        long rejected = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.REJECTED);
        long cancelled = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.CANCELLED);

        return new ProviderBookingSummaryResponse(
                total,
                pending,
                awaitingDeposit,
                accepted,
                confirmed,
                inProgress,
                completed,
                rejected,
                cancelled,
                bookingRepository.findProviderQueueVehicleNames(providerId)
        );
    }

    @Override
    public ProviderBookingDetailResponse getBookingDetail(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        Vehicle vehicle = booking.getVehicle();
        User renterUser = booking.getRenter().getUser();

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(vehicle.getId()));
        String coverUrl = coverUrls.get(vehicle.getId());

        List<String> vehicleImageUrls = List.of();
        try {
            VehicleDetailResponse vehicleDetail = vehicleService.getVehicleDetail(vehicle.getId());
            if (vehicleDetail != null && vehicleDetail.imageUrls() != null) {
                vehicleImageUrls = vehicleDetail.imageUrls();
            }
        } catch (Exception ignored) {
        }

        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElse(null);

        List<BookingCharge> chargesList = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        List<BookingChargeResponse> chargeResponses = chargesList.stream()
                .map(this::toChargeResponse)
                .toList();

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        List<ProviderBookingDetailResponse.PaymentTransactionDetailResponse> transactionResponses = transactions.stream()
                .map(t -> new ProviderBookingDetailResponse.PaymentTransactionDetailResponse(
                        t.getId(),
                        t.getTransactionCode(),
                        t.getType() != null ? t.getType().name() : null,
                        t.getAmount(),
                        t.getDirection() != null ? t.getDirection().name() : null,
                        t.getMethod() != null ? t.getMethod().name() : null,
                        t.getStatus() != null ? t.getStatus().name() : null,
                        t.getReceiptUrl(),
                        t.getPaidAt(),
                        t.getCreatedAt()
                ))
                .toList();

        List<ProviderBookingDetailResponse.TimelineEventResponse> timeline = buildTimeline(booking);

        String rentalType = "Theo ngày";
        String rentalDuration = "1 ngày";
        if (booking.getPlan() != null) {
            rentalType = determineRentalType(booking.getPlan().getPlanType());
            rentalDuration = calculateRentalDuration(booking);
        }

        String driverName = null;
        String driverPhone = null;
        String driverLicenseNo = null;
        String driverLicenseClass = null;
        String driverPortraitUrl = null;
        BigDecimal driverRatingAvg = null;
        Long driverId = null;

        if (booking.getDriver() != null) {
            Driver driver = booking.getDriver();
            driverId = driver.getId();
            driverLicenseNo = driver.getLicenseNo();
            driverLicenseClass = driver.getLicenseClass();
            driverPortraitUrl = driver.getPortraitUrl();
            driverRatingAvg = driver.getRatingAvg();
            if (driver.getUser() != null) {
                driverName = driver.getUser().getFullName();
                driverPhone = driver.getUser().getPhone();
            }
        }

        String cancelledByName = null;
        if (booking.getCancelledBy() != null) {
            cancelledByName = booking.getCancelledBy().getFullName();
        }

        ProviderBookingDetailResponse.DriverSummary driverSummary = null;
        if (booking.getDriver() != null) {
            Driver d = booking.getDriver();
            String name = (d.getUser() != null) ? d.getUser().getFullName() : null;
            String phone = (d.getUser() != null) ? d.getUser().getPhone() : null;
            driverSummary = new ProviderBookingDetailResponse.DriverSummary(
                    d.getId(),
                    name,
                    phone,
                    d.getLicenseNo(),
                    d.getLicenseClass(),
                    d.getPortraitUrl(),
                    d.getRatingAvg()
            );
        }

        String executionStatus = DriverTripServiceImpl.deriveExecutionStatus(booking);

        ProviderBookingDetailResponse.TripSummary tripSummary = new ProviderBookingDetailResponse.TripSummary(
                booking.getId(),
                executionStatus,
                booking.getActualArrivedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt()
        );

        return new ProviderBookingDetailResponse(
                booking.getId(),
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getStatus(),
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                booking.getPassengerCount() != null ? booking.getPassengerCount() : 1,
                booking.getContractUrl(),
                booking.getTotalAmount(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getRenterConfirmedAt(),
                booking.getProviderConfirmedAt(),
                booking.getCancelledAt(),
                booking.getCancelReason(),
                cancelledByName,
                booking.getCreatedAt(),
                booking.getUpdatedAt(),

                booking.getRenter().getId(),
                renterUser.getFullName(),
                renterUser.getPhone(),
                renterUser.getEmail(),
                renterUser.getAvatarUrl(),

                vehicle.getId(),
                vehicle.getBrand() + " " + vehicle.getModel(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                coverUrl,
                vehicleImageUrls,

                rentalType,
                rentalDuration,
                snapshot != null ? snapshot.getSlotPrice() : (booking.getPlan() != null ? booking.getPlan().getSlotPrice() : null),
                snapshot != null ? snapshot.getSlotLengthMinutes() : (booking.getPlan() != null ? booking.getPlan().getSlotLengthMinutes() : null),
                snapshot != null ? snapshot.getPlanType() : (booking.getPlan() != null ? booking.getPlan().getPlanType() : null),
                snapshot != null ? snapshot.getDepositRatio() : null,
                snapshot != null ? snapshot.getUpfrontAmount() : null,
                snapshot != null ? snapshot.getAllowedOvertimeMinutes() : (booking.getPlan() != null ? booking.getPlan().getAllowedOvertimeMinutes() : null),
                snapshot != null ? snapshot.getOvertimeSurchargeRatio() : (booking.getPlan() != null ? booking.getPlan().getOvertimeSurchargeRatio() : null),
                snapshot != null ? snapshot.getWaitingFreeMinutes() : (booking.getPlan() != null ? booking.getPlan().getWaitingFreeMinutes() : null),
                snapshot != null ? snapshot.getWaitingRate() : (booking.getPlan() != null ? booking.getPlan().getWaitingRate() : null),
                snapshot != null ? snapshot.getWeekendSurchargeRatio() : (booking.getPlan() != null ? booking.getPlan().getWeekendSurchargeRatio() : null),
                snapshot != null ? snapshot.getFreeRadiusKm() : (booking.getPlan() != null ? booking.getPlan().getFreeRadiusKm() : null),
                snapshot != null ? snapshot.getOutOfRadiusRate() : (booking.getPlan() != null ? booking.getPlan().getOutOfRadiusRate() : null),

                driverId,
                driverName,
                driverPhone,
                driverLicenseNo,
                driverLicenseClass,
                driverPortraitUrl,
                driverRatingAvg,

                driverSummary,
                tripSummary,

                chargeResponses,
                transactionResponses,
                timeline,
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                executionStatus,
                bookingTrackingService.getLatestLocation(booking.getId()),
                booking.getStatus() == BookingStatus.CONFIRMED || (booking.isDriverReplacementRequired()
                        && java.util.Set.of(BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED).contains(booking.getStatus())),
                booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED,
                resolveDriverRejectionReason(booking),
                resolvePaymentStatus(booking, transactions),
                plannedEnd(booking),
                booking.getPricingItems().stream().map(item ->
                        new RentalQuoteResponse.Item(
                                item.getPlanId(), item.getPlanType(), item.getSlotLengthMinutes(),
                                item.getUnitPrice(), item.getQuantity())).toList(),
                snapshot != null ? snapshot.getDispatchOriginLatitude() : null,
                snapshot != null ? snapshot.getDispatchOriginLongitude() : null,
                snapshot != null ? snapshot.getDistanceKm() : null,
                chargeableKm(snapshot),
                snapshot != null ? snapshot.getPickupFee() : null
        );
    }

    @Override
    @Transactional
    public ProviderBookingResponse acceptBooking(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);
        vehicleService.findVehicleByIdForUpdate(booking.getVehicle().getId()).orElseThrow();

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể chấp nhận đơn ở trạng thái Chờ duyệt (PENDING)");
        }
        if (booking.getPickupTime() == null || !booking.getPickupTime().isAfter(clock.instant().plus(Duration.ofHours(1)))) {
            throw new ConflictException("Không thể duyệt đơn đã quá hạn nhận xe hoặc không còn đủ thời gian xử lý");
        }

        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElse(null);
        boolean yearly = booking.getPlan().getPlanType() == com.fptufa26.ecomotion.vehicle.enums.PlanType.YEARLY;
        int slots = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        int slotLengthMinutes = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        Instant end = booking.getPlannedEndAt() != null ? booking.getPlannedEndAt() : booking.getPickupTime().plus(
                Duration.ofMinutes((long) slotLengthMinutes * Math.max(1, slots)));
        if (yearly) {
            if (!scheduleService.hasUnexpiredHold(booking.getId(), clock.instant())) {
                throw new ConflictException("Thời hạn yêu cầu thuê năm đã hết. Người thuê cần gửi yêu cầu mới.");
            }
            DrivingLicenseEligibilityPolicy.requireEligibleForBooking(
                    booking.getRenter(), booking.getVehicle(), booking.getPickupTime(), end);
            scheduleService.release(booking.getId());
        }
        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(
                booking.getVehicle(), booking.getPickupTime(), end);
        if (!scheduleService.isRangeFree(booking.getVehicle().getId(), window.start(), window.end())) {
            throw new ConflictException("Xe không còn trống trong khoảng thời gian đã chọn");
        }

        booking.setStatus(yearly ? BookingStatus.ACCEPTED : BookingStatus.AWAITING_DEPOSIT);
        Booking saved = bookingRepository.save(booking);
        scheduleService.hold(booking.getVehicle(), booking, booking.getPickupTime(), end,
                clock.instant().plus(yearly ? Duration.ofHours(24) : Duration.ofMinutes(15)));

        if (!yearly) {
            paymentRequestService.createDepositPaymentRequest(saved);
        }
        bookingNotificationService.changed(saved, "BOOKING_CHANGED");

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(saved.getVehicle().getId()));
        return toResponse(saved, coverUrls.get(saved.getVehicle().getId()));
    }

    @Override
    @Transactional
    public ProviderBookingResponse denyBooking(Long currentUserId, String bookingCodeOrId, String reason) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể từ chối đơn ở trạng thái Chờ duyệt (PENDING)");
        }

        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new IllegalArgumentException("Lý do từ chối phải từ 1 đến 500 ký tự");
        booking.setStatus(BookingStatus.REJECTED);
        booking.setCancelledAt(null);
        booking.setCancelledBy(null);
        booking.setCancelReason(null);
        booking.setOperationalResolutionReason(reason == null ? null : reason.trim());
        bookingRepository.save(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(booking.getVehicle().getId()));
        return toResponse(booking, coverUrls.get(booking.getVehicle().getId()));
    }

    @Override
    @Transactional
    public ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId, Long driverId) {
        return assignDriver(currentUserId, bookingCodeOrId, driverId, DriverAssignmentMode.MANUAL);
    }

    @Override
    @Transactional
    public ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId,
                                                Long driverId, DriverAssignmentMode mode) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);

        BookingStatus oldStatus = booking.getStatus();
        boolean replacingUnavailableDriver = booking.isDriverReplacementRequired()
                && (oldStatus == BookingStatus.DRIVER_ASSIGNED
                || oldStatus == BookingStatus.DRIVER_CONFIRMED
                || oldStatus == BookingStatus.DRIVER_ARRIVED);
        if (oldStatus != BookingStatus.CONFIRMED && !replacingUnavailableDriver) {
            throw new ConflictException("Chỉ có thể phân công tài xế sau khi khoản cọc đã được xác nhận");
        }

        Long selectedDriverId = driverId;
        if (mode == DriverAssignmentMode.AUTO) {
            DriverCandidateResponse suggested = driverOptimizationService.suggestDriver(booking.getId(), provider.getId()).suggestedDriver();
            if (suggested == null) {
                throw new ConflictException("Chưa có tài xế phù hợp và rảnh cho lịch này");
            }
            selectedDriverId = suggested.driverId();
        }
        userService.findDriverByIdForUpdate(selectedDriverId)
                .orElseThrow(() -> new ConflictException("Không tìm thấy tài xế"));
        Driver driver = driverAssignmentService.requireEligibleForUpdate(
                provider.getId(), selectedDriverId,
                booking.getPickupTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        DriverCandidateResponse candidate = driverOptimizationService.evaluateCandidates(booking.getId(), provider.getId()).stream()
                .filter(item -> item.driverId().equals(driver.getId()))
                .findFirst().orElseThrow(() -> new ConflictException("Tài xế không thuộc nhà cung cấp"));
        if (!candidate.eligible()) {
            throw new ConflictException(candidate.ineligibilityReason());
        }
        if (replacingUnavailableDriver && booking.getDriver() != null
                && booking.getDriver().getId().equals(driver.getId())) {
            throw new ConflictException("Tài xế thay thế phải khác tài xế đã báo sự cố");
        }
        if (!driverAvailabilityService.isAvailable(
                driver.getId(), booking.getVehicle(), booking.getPickupTime(), plannedEnd(booking), booking.getId())) {
            throw new ConflictException("Tài xế đã có chuyến khác trùng thời gian hoặc khoảng đệm");
        }

        Instant now = clock.instant();
        booking.setDriver(driver);
        booking.setAssignedAt(now);
        booking.setDriverConfirmedAt(null);
        booking.setActualArrivedAt(null);
        booking.setAssignmentExpiresAt(driverOptimizationService.assignmentDeadline());
        if (replacingUnavailableDriver) {
            booking.setDriverReplacementRequired(false);
            booking.setDriverIssueResolvedAt(now);
        }
        booking.setStatus(BookingStatus.DRIVER_ASSIGNED);
        booking.setAssignmentMode(mode == DriverAssignmentMode.AUTO ? AssignmentMode.AUTO_SUGGEST : AssignmentMode.MANUAL);
        Booking saved = bookingRepository.saveAndFlush(booking);
        bookingNotificationService.changed(booking, "DRIVER_ASSIGNED");
        recordDriverAssignment(saved, driver, now);
        driverStatusService.updateStatusTimestamp(driver.getId());

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(saved.getVehicle().getId()));
        return toResponse(saved, coverUrls.get(saved.getVehicle().getId()));
    }

    @Override
    public List<DriverCandidateResponse> getAssignmentCandidates(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return driverOptimizationService.evaluateCandidates(booking.getId(), provider.getId());
    }

    @Override
    public AssignmentSuggestionResponse suggestDriverAssignment(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        boolean replacement = booking.isDriverReplacementRequired()
                && java.util.Set.of(BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED).contains(booking.getStatus());
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ASSIGNED && !replacement)
            throw new ConflictException("Đơn thuê chưa ở giai đoạn phân công tài xế");
        return driverOptimizationService.suggestDriver(booking.getId(), provider.getId());
    }

    @Override
    @Transactional
    public com.fptufa26.ecomotion.booking.dto.response.AutoAssignmentResponse autoAssignDriver(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
            throw new ConflictException("Chỉ có thể phân công tài xế sau khi khoản cọc đã được xác nhận");
        }

        var execution = driverOptimizationService.autoAssignWithResult(booking.getId());
        Driver driver = execution.driver();
        if (driver == null) {
            throw new ConflictException("Chưa có tài xế phù hợp với khung giờ này");
        }
        booking.setAssignmentMode(AssignmentMode.FULL_AUTO);

        Booking refreshed = bookingRepository.findById(booking.getId()).orElse(booking);
        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(refreshed.getVehicle().getId()));
        return new com.fptufa26.ecomotion.booking.dto.response.AutoAssignmentResponse(
                toResponse(refreshed, coverUrls.get(refreshed.getVehicle().getId())), execution.solverStatus());
    }

    @Override
    @Transactional
    public void resolveIncident(Long currentUserId, String bookingCodeOrId, Long incidentId, ResolveIncidentRequest request) {
        tripIncidentService.resolveIncident(currentUserId, bookingCodeOrId, incidentId, request);
    }

    @Override
    public List<TripIncidentResponse> getBookingIncidents(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return tripIncidentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .map(TripIncidentResponse::fromEntity)
                .toList();
    }

    @Override
    public List<TripEvidenceResponse> getBookingEvidence(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return tripEvidenceRepository.findByBooking_IdOrderByRecordedAtAsc(booking.getId()).stream()
                .map(TripEvidenceResponse::fromEntity)
                .toList();
    }

    @Override
    public List<BookingChargeResponse> getCharges(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        List<BookingCharge> charges = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        return charges.stream().map(this::toChargeResponse).toList();
    }

    @Override
    @Transactional
    public BookingChargeResponse addCharge(Long currentUserId, String bookingCodeOrId, AddChargeRequest request) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);

        if (booking.getStatus() != BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION
                || booking.getActualEndAt() == null) {
            throw new ConflictException("Chỉ có thể thêm phụ phí sau khi tài xế kết thúc chuyến đi");
        }
        if (request.chargeType() == ChargeType.BASE_RENTAL || request.chargeType() == ChargeType.DEPOSIT
                || request.chargeType() == ChargeType.OUT_OF_RADIUS || request.chargeType() == ChargeType.WEEKEND
                || request.chargeType() == ChargeType.WAITING) {
            throw new ConflictException("Loại phụ phí này không được thêm sau chuyến");
        }

        if (request.evidenceUrl() == null || request.evidenceUrl().isBlank())
            throw new ConflictException("Cần bằng chứng cho phụ phí ghi thủ công");
        java.net.URI proof;
        try { proof = java.net.URI.create(request.evidenceUrl().trim()); }
        catch (IllegalArgumentException exception) { throw new ConflictException("Đường dẫn bằng chứng không hợp lệ"); }
        if (!("https".equalsIgnoreCase(proof.getScheme()) || "http".equalsIgnoreCase(proof.getScheme())) || proof.getHost() == null)
            throw new ConflictException("Bằng chứng phải là đường dẫn HTTP/HTTPS hợp lệ");
        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(request.chargeType());
        charge.setDescription(request.description());
        charge.setQuantity(request.quantity());
        charge.setUnitPrice(request.unitPrice());
        charge.setAppliedRate(request.appliedRate());
        BookingPolicySnapshot policy = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new ConflictException("Thiếu bản chốt chính sách giá của đơn thuê"));
        if (policy.getPlanType() == com.fptufa26.ecomotion.vehicle.enums.PlanType.YEARLY)
            throw new ConflictException("Gói năm không có phụ phí");
        if (request.description() == null || request.description().isBlank())
            throw new ConflictException("Cần lý do phụ phí");
        if (request.chargeType() == ChargeType.OTHER && request.description().trim().length() < 20)
            throw new ConflictException("Phụ phí khác cần mô tả cụ thể ít nhất 20 ký tự");
        String clientRequestId = request.clientRequestId() == null || request.clientRequestId().isBlank()
                ? null : request.clientRequestId().trim();
        BigDecimal price;
        BigDecimal rate;
        Integer quantity;
        BigDecimal amount;
        if (request.chargeType() == ChargeType.OVERTIME) {
            if (request.quantity() == null || request.quantity() < 1 || request.quantity() > 1000000)
                throw new ConflictException("Cần số slot quá giờ hợp lệ");
            price = policy.getSlotPrice();
            rate = BigDecimal.ONE.add(java.util.Objects.requireNonNullElse(policy.getOvertimeSurchargeRatio(), BigDecimal.ZERO));
            if (price == null || price.signum() < 0) throw new ConflictException("Thiếu chính sách phụ phí hợp lệ");
            quantity = request.quantity();
            amount = price.multiply(rate).multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            if (request.amount() == null || request.amount().signum() <= 0)
                throw new ConflictException("Số tiền phụ phí phải lớn hơn 0");
            price = request.amount().setScale(2, java.math.RoundingMode.HALF_UP);
            rate = BigDecimal.ONE;
            quantity = 1;
            amount = price;
        }
        if (amount.precision() > 14) throw new ConflictException("Số tiền phụ phí vượt giới hạn");
        if (clientRequestId != null) {
            var sameAmount = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId()).stream()
                    .filter(existing -> clientRequestId.equals(existing.getClientRequestId()))
                    .findFirst();
            if (sameAmount.isPresent()) {
                BookingCharge prior = sameAmount.get();
                boolean samePayload = prior.getChargeType() == request.chargeType()
                        && request.description().trim().equals(prior.getDescription())
                        && prior.getAmount().compareTo(amount) == 0;
                if (!samePayload) {
                    throw new ConflictException("Mã yêu cầu phụ phí đã dùng cho nội dung khác");
                }
                return toChargeResponse(prior);
            }
        }
        charge.setUnitPrice(price);
        charge.setAppliedRate(rate);
        charge.setQuantity(quantity);
        charge.setAmount(amount);
        charge.setDescription(request.description().trim());
        charge.setCreatedBy(provider.getUser());
        charge.setWaived(false);
        charge.setClientRequestId(clientRequestId);
        charge.setConfirmationStatus(com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.PENDING);
        charge.setEvidenceUrl(request.evidenceUrl());

        BookingCharge saved = bookingChargeRepository.save(charge);
        bookingNotificationService.changed(booking, "CHARGES_CHANGED");

        return toChargeResponse(saved);
    }

    @Override
    @Transactional
    public BookingChargeResponse withdrawCharge(Long currentUserId, String bookingCodeOrId, Long chargeId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);
        BookingCharge charge = bookingChargeRepository.findByIdAndBooking_Id(chargeId, booking.getId())
                .orElseThrow(() -> new com.fptufa26.ecomotion.common.exception.NotFoundException("Không tìm thấy phụ phí"));
        if (charge.getConfirmationStatus() != com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.PENDING || charge.isWaived()) {
            throw new ConflictException("Chỉ rút được phụ phí đang chờ xác nhận");
        }
        if (disputeService.hasOpenDispute(booking.getId())) {
            throw new ConflictException("Không rút phụ phí khi đơn đang có tranh chấp");
        }
        charge.setWaived(true);
        bookingNotificationService.changed(booking, "CHARGES_CHANGED");
        return toChargeResponse(bookingChargeRepository.save(charge));
    }

    @Override
    @Transactional
    public SettlementResultResponse finalizeSettlement(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);

        if (booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT) {
            PaymentRequest existing = paymentRequestService.createFinalBalancePaymentRequest(booking.getId());
            return new SettlementResultResponse(
                    booking.getBookingCode(),
                    booking.getStatus(),
                    existing.getId(),
                    existing.getAmount().subtract(existing.getPaidAmount()).max(BigDecimal.ZERO));
        }
        if (booking.getStatus() != BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION) {
            throw new ConflictException("Đơn thuê chưa ở bước chốt quyết toán");
        }
        if (booking.getProviderConfirmedAt() == null || !booking.isEndAccepted()) {
            throw new ConflictException("Cần xác nhận kết thúc từ cả tài xế và người thuê trước khi chốt quyết toán");
        }
        if (disputeService.hasOpenDispute(booking.getId())) {
            throw new ConflictException("Không thể chốt quyết toán khi đơn thuê đang có tranh chấp");
        }
        boolean unresolvedCharge = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId()).stream()
                .anyMatch(charge -> !charge.isWaived() && charge.getConfirmationStatus()
                        != com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.ACCEPTED);
        if (unresolvedCharge) {
            throw new ConflictException("Còn phụ phí chưa được người thuê xác nhận hoặc đang tranh chấp");
        }

        BigDecimal remaining = paymentRequestService.calculateRemainingAmount(booking.getId());
        BigDecimal obligation = booking.getTotalAmount() == null ? BigDecimal.ZERO : booking.getTotalAmount();
        obligation = obligation.add(bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId()).stream()
                .filter(charge -> !charge.isWaived()
                        && charge.getConfirmationStatus() == com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.ACCEPTED
                        && charge.getChargeType() != ChargeType.BASE_RENTAL
                        && charge.getChargeType() != ChargeType.DEPOSIT
                        && charge.getChargeType() != ChargeType.WEEKEND)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal openRefund = refundEventService.openAllocated(booking.getId(), obligation);
        if (openRefund.signum() > 0) {
            refundEventService.reserve(booking.getId(), "SETTLE:" + booking.getId(), openRefund,
                    "Hoàn phần đã thu vượt nghĩa vụ quyết toán", provider.getUser());
        }
        if (remaining.signum() <= 0) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");
            return new SettlementResultResponse(
                    booking.getBookingCode(), BookingStatus.COMPLETED, null, BigDecimal.ZERO.setScale(2));
        }

        PaymentRequest paymentRequest = paymentRequestService.createFinalBalancePaymentRequest(booking.getId());
        booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        bookingRepository.save(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");
        return new SettlementResultResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                paymentRequest.getId(),
                paymentRequest.getAmount().subtract(paymentRequest.getPaidAmount()).max(BigDecimal.ZERO));
    }

    @Override
    public List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        List<Driver> drivers = driverAssignmentService.listEligible(provider.getId(), LocalDate.now());

        List<BookingStatus> activeStatuses = List.of(BookingStatus.ACCEPTED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
        Instant now = clock.instant();

        return drivers.stream().map(driver -> {
            long activeBookings = bookingRepository.countByDriver_IdAndStatusIn(driver.getId(), activeStatuses);
            String fullName = driver.getUser() != null ? driver.getUser().getFullName() : null;
            String phone = driver.getUser() != null ? driver.getUser().getPhone() : null;

            com.fptufa26.ecomotion.user.enums.DriverActivityStatus act = driver.getActivityStatus() != null
                    ? driver.getActivityStatus()
                    : com.fptufa26.ecomotion.user.enums.DriverActivityStatus.AVAILABLE;
            boolean onTrip = bookingRepository.existsByDriver_IdAndStatus(driver.getId(), BookingStatus.IN_PROGRESS);

            com.fptufa26.ecomotion.user.enums.DriverOperationalStatus op;
            boolean eligible;
            String ineligibilityReason = null;

            if (onTrip) {
                op = com.fptufa26.ecomotion.user.enums.DriverOperationalStatus.ON_TRIP;
                eligible = false;
                ineligibilityReason = "Tài xế đang thực hiện chuyến đi khác (ON_TRIP)";
            } else if (act == com.fptufa26.ecomotion.user.enums.DriverActivityStatus.OFFLINE) {
                op = com.fptufa26.ecomotion.user.enums.DriverOperationalStatus.OFFLINE;
                eligible = false;
                ineligibilityReason = "Tài xế đang ngoại tuyến (OFFLINE)";
            } else if (act == com.fptufa26.ecomotion.user.enums.DriverActivityStatus.UNAVAILABLE) {
                op = com.fptufa26.ecomotion.user.enums.DriverOperationalStatus.UNAVAILABLE;
                eligible = false;
                ineligibilityReason = "Tài xế đang chủ động tạm nghỉ (UNAVAILABLE)";
            } else {
                List<Booking> upcoming = bookingRepository.findUpcomingDriverBookings(driver.getId(), List.of(
                        BookingStatus.CONFIRMED, BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED
                ), now);
                if (!upcoming.isEmpty()) {
                    op = com.fptufa26.ecomotion.user.enums.DriverOperationalStatus.SCHEDULED;
                } else {
                    op = com.fptufa26.ecomotion.user.enums.DriverOperationalStatus.AVAILABLE;
                }
                eligible = true;
            }

            return new AvailableDriverResponse(
                    driver.getId(),
                    driver.getUser() != null ? driver.getUser().getId() : null,
                    fullName,
                    phone,
                    driver.getLicenseNo(),
                    driver.getLicenseClass(),
                    driver.getLicenseExpiry(),
                    driver.getPortraitUrl(),
                    driver.getStatus(),
                    act,
                    op,
                    driver.getRatingAvg(),
                    driver.getRatingCount(),
                    activeBookings,
                    eligible,
                    ineligibilityReason
            );
        }).toList();
    }

    @Override
    public List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId, String bookingCode) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = bookingRepository.findByBookingCodeAndVehicle_Provider_Id(
                        bookingCode.trim().toUpperCase(Locale.ROOT), provider.getId())
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê " + bookingCode));
        List<Driver> drivers = driverAvailabilityService.findAvailable(
                provider.getId(), booking.getVehicle(), booking.getPickupTime(), plannedEnd(booking), booking.getId());
        return drivers.stream()
                .map(driver -> new AvailableDriverResponse(
                        driver.getId(),
                        driver.getUser() != null ? driver.getUser().getId() : null,
                        driver.getUser() != null ? driver.getUser().getFullName() : null,
                        driver.getUser() != null ? driver.getUser().getPhone() : null,
                        driver.getLicenseNo(),
                        driver.getLicenseClass(),
                        driver.getLicenseExpiry(),
                        driver.getPortraitUrl(),
                        driver.getStatus(),
                        driver.getActivityStatus(),
                        driverStatusService.calculateOperationalStatus(driver),
                        driver.getRatingAvg(),
                        driver.getRatingCount(),
                        bookingRepository.countByDriver_IdAndStatusIn(driver.getId(), driverOccupyingStatuses()),
                        true,
                        null))
                .toList();
    }

    private List<BookingStatus> driverOccupyingStatuses() {
        return List.of(
                BookingStatus.CONFIRMED,
                BookingStatus.DRIVER_ASSIGNED,
                BookingStatus.DRIVER_CONFIRMED,
                BookingStatus.DRIVER_ARRIVED,
                BookingStatus.IN_PROGRESS);
    }

    private Instant plannedEnd(Booking booking) {
        if (booking.getPlannedEndAt() != null) return booking.getPlannedEndAt();
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElse(null);
        int slotLength = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        int quantity = bookingChargeRepository.findByBooking_IdAndChargeType(
                        booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) slotLength * Math.max(1, quantity)));
    }

    @Override
    public byte[] exportBookingsCsv(Long currentUserId, String status) {
        List<ProviderBookingResponse> bookings = listBookings(currentUserId, status);

        return writeBookingsCsv(bookings);
    }

    private byte[] writeBookingsCsv(List<ProviderBookingResponse> bookings) {
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append("Mã đơn,Khách thuê,Xe,Biển số,Hình thức,Thời lượng,Giờ đón,Điểm đón,Tài xế,Trạng thái,Tổng tiền,Ngày tạo\n");

        for (ProviderBookingResponse b : bookings) {
            sb.append(escapeCsv(b.bookingCode())).append(",")
                    .append(escapeCsv(b.renterName())).append(",")
                    .append(escapeCsv(b.vehicleName())).append(",")
                    .append(escapeCsv(b.licensePlate())).append(",")
                    .append(escapeCsv(b.rentalType())).append(",")
                    .append(escapeCsv(b.rentalDuration())).append(",")
                    .append(escapeCsv(b.pickupTime() != null ? DATE_TIME_FORMATTER.format(b.pickupTime()) : "")).append(",")
                    .append(escapeCsv(b.pickupAddress())).append(",")
                    .append(escapeCsv(b.driverName() != null ? b.driverName() : "Chưa phân công")).append(",")
                    .append(escapeCsv(b.status() != null ? b.status().name() : "")).append(",")
                    .append(b.totalAmount() != null ? b.totalAmount().toPlainString() : "0").append(",")
                    .append(escapeCsv(b.createdAt() != null ? DATE_TIME_FORMATTER.format(b.createdAt()) : ""))
                    .append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public PageResponse<ProviderBookingResponse> filterBookings(Long userId,
            com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter filter, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Phân trang không hợp lệ");
        Provider provider = resolveProvider(userId);
        var spec = BookingSpecification.forProviderQueue(provider.getId(), parseStatus(filter.status()))
                .and(com.fptufa26.ecomotion.booking.specification.ProviderWorkflowSpecification.filters(filter));
        String sort = filter.sort() == null ? "NEWEST" : filter.sort();
        Sort ordering = switch (sort) {
            case "NEWEST" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            case "CREATED_DESC" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "OLDEST" -> Sort.by(Sort.Direction.ASC, "pickupTime");
            case "PRICE_HIGH" -> Sort.by(Sort.Direction.DESC, "totalAmount");
            case "PRICE_LOW" -> Sort.by(Sort.Direction.ASC, "totalAmount");
            default -> throw new IllegalArgumentException("Kiểu sắp xếp không hợp lệ");
        };
        Page<Booking> page = bookingRepository.findAll(spec, new QueuePage(offset, limit, ordering.and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(toResponses(page.getContent()), page.getTotalElements(), offset, limit, offset + (long) limit < page.getTotalElements());
    }

    @Override
    public byte[] exportFilteredBookings(Long userId, com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter filter) {
        List<ProviderBookingResponse> rows = new java.util.ArrayList<>();
        for (int offset = 0; ; offset += 100) {
            var page = filterBookings(userId, filter, offset, 100);
            rows.addAll(page.items());
            if (!page.hasMore()) return writeBookingsCsv(rows);
            if (rows.size() >= 10000) throw new IllegalArgumentException("Thu hẹp bộ lọc để xuất tối đa 10000 đơn");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void recordDriverAssignment(Booking booking, Driver driver, Instant now) {
        driverOptimizationService.recallActiveAssignments(booking.getId());
        DriverAssignment assignment = new DriverAssignment();
        assignment.setBooking(booking);
        assignment.setDriver(driver);
        assignment.setStatus(AssignmentStatus.PENDING_RESPONSE);
        assignment.setAssignedAt(now);
        assignment.setExpiresAt(booking.getAssignmentExpiresAt());
        assignment.setRoundNumber(booking.getReassignCount() != null ? booking.getReassignCount() + 1 : 1);
        driverAssignmentRepository.save(assignment);
    }

    private Provider resolveProvider(Long currentUserId) {
        return userService.findProviderProfile(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Current user has no provider profile"));
    }

    private Booking resolveBooking(Provider provider, String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new BookingNotFoundException("Mã đơn thuê không hợp lệ");
        }

        String bookingCode = bookingCodeOrId.trim().toUpperCase(Locale.ROOT);
        return bookingRepository.findByBookingCodeAndVehicle_Provider_Id(bookingCode, provider.getId())
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingCode));
    }

    private Booking resolveBookingForUpdate(Provider provider, String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new BookingNotFoundException("Mã đơn thuê không hợp lệ");
        }
        String bookingCode = bookingCodeOrId.trim().toUpperCase(Locale.ROOT);
        return bookingRepository.findByBookingCodeAndProviderIdForUpdate(bookingCode, provider.getId())
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingCode));
    }

    private Pageable applyDefaultSortIfUnsorted(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, DEFAULT_SORT);
        }
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        }
        return pageable;
    }

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }
    }

    private List<ProviderBookingResponse> toResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Map<Long, String> coverUrls = getCoverUrlsForBookings(bookings);
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, List<PaymentTransaction>> txMap = bookingIds.isEmpty() ? Collections.emptyMap()
                : paymentTransactionRepository.findByBooking_IdIn(bookingIds).stream()
                        .collect(Collectors.groupingBy(tx -> tx.getBooking().getId()));
        return bookings.stream()
                .map(b -> toResponse(b, coverUrls.get(b.getVehicle().getId()), txMap.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    private Map<Long, String> getCoverUrlsForBookings(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> vehicleIds = bookings.stream()
                .map(b -> b.getVehicle().getId())
                .collect(Collectors.toSet());
        return vehicleService.getCoverImageUrls(vehicleIds);
    }

    private ProviderBookingResponse toResponse(Booking booking, String coverUrl) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId());
        return toResponse(booking, coverUrl, transactions);
    }

    private ProviderBookingResponse toResponse(Booking booking, String coverUrl, List<PaymentTransaction> transactions) {
        Vehicle vehicle = booking.getVehicle();
        User renterUser = booking.getRenter().getUser();
        String renterName = renterUser.getFullName() != null && !renterUser.getFullName().isBlank()
                ? renterUser.getFullName()
                : renterUser.getEmail();

        Long driverId = null;
        String driverName = null;
        if (booking.getDriver() != null) {
            driverId = booking.getDriver().getId();
            if (booking.getDriver().getUser() != null) {
                driverName = booking.getDriver().getUser().getFullName();
            }
        }

        String rentalType = "Theo ngày";
        String rentalDuration = "1 ngày";
        if (booking.getPlan() != null) {
            rentalType = determineRentalType(booking.getPlan().getPlanType());
            rentalDuration = calculateRentalDuration(booking);
        }

        var risk = vehicleOperationalRiskService.assess(booking);
        String paymentStatus = resolvePaymentStatus(booking, transactions);
        return new ProviderBookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                vehicle.getBrand() + " " + vehicle.getModel(),
                coverUrl,
                vehicle.getLicensePlate(),
                renterName,
                booking.getPassengerCount() != null ? booking.getPassengerCount() : 1,
                rentalType,
                rentalDuration,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                driverId,
                driverName,
                booking.getStatus(),
                DriverTripServiceImpl.deriveExecutionStatus(booking),
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                risk.atRisk() || booking.isDriverReplacementRequired(),
                booking.isDriverReplacementRequired() ? booking.getDriverIssueReason() : risk.message(),
                paymentStatus
        );
    }

    private String resolvePaymentStatus(Booking booking, List<PaymentTransaction> transactions) {
        return paymentStates.evaluate(booking, transactions == null ? List.of() : transactions).status().name();
    }

    @Override
    @Transactional
    public OperationalResolutionResponse resolveOperationalRisk(
            Long currentUserId, String bookingCodeOrId, ResolveOperationalRiskRequest request) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBookingForUpdate(provider, bookingCodeOrId);
        requireOperationallyMutable(booking);

        if (request.resolution() != OperationalResolutionType.CANCEL_AND_REFUND) {
            var proposal = booking.getOperationalProposal();
            if (proposal == null || !Boolean.TRUE.equals(proposal.getAccepted())
                    || !java.util.Objects.equals(proposal.getRespondedBy(), booking.getRenter().getUser().getId())
                    || proposal.getResolution() != request.resolution()
                    || !java.util.Objects.equals(proposal.getVehicleId(), request.replacementVehicleId())
                    || !java.util.Objects.equals(proposal.getPickupTime(), request.newPickupTime())) {
                throw new ConflictException("Khách thuê phải chấp thuận đúng đề xuất trước khi đổi xe hoặc giờ đón");
            }
        }

        Instant now = clock.instant();
        BigDecimal refundAmount = BigDecimal.ZERO;
        String message;
        switch (request.resolution()) {
            case CHANGE_VEHICLE -> {
                if (request.replacementVehicleId() == null) {
                    throw new IllegalArgumentException("Vui lòng chọn xe thay thế");
                }
                Vehicle replacement = vehicleService.requireApprovedProviderVehicleForUpdate(
                        request.replacementVehicleId(), provider.getId());
                if (replacement.getId().equals(booking.getVehicle().getId())) {
                    throw new IllegalArgumentException("Xe thay thế phải khác xe hiện tại");
                }
                if (replacement.getSeatCount() < booking.getPassengerCount() + 1) {
                    throw new IllegalArgumentException("Xe thay thế không đủ chỗ cho hành khách và tài xế");
                }
                Instant end = plannedEnd(booking, booking.getPickupTime());
                var window = AvailabilityPolicy.bufferedWindow(replacement, booking.getPickupTime(), end);
                scheduleService.moveBooking(booking, replacement, window.start(), window.end());
                booking.setPreviousVehicleId(booking.getVehicle().getId());
                booking.setVehicle(replacement);
                message = "Đã đổi sang xe " + replacement.getLicensePlate();
            }
            case RESCHEDULE -> {
                if (request.newPickupTime() == null || !request.newPickupTime().isAfter(now.plus(Duration.ofHours(1)))) {
                    throw new IllegalArgumentException("Giờ nhận mới phải sau hiện tại ít nhất 1 giờ");
                }
                Instant oldPickup = booking.getPickupTime();
                vehicleService.findVehicleByIdForUpdate(booking.getVehicle().getId()).orElseThrow();
                Instant end = plannedEnd(booking, request.newPickupTime());
                var window = AvailabilityPolicy.bufferedWindow(booking.getVehicle(), request.newPickupTime(), end);
                scheduleService.moveBooking(booking, booking.getVehicle(), window.start(), window.end());
                booking.setPreviousPickupTime(oldPickup);
                booking.setPickupTime(request.newPickupTime());
                booking.setPlannedEndAt(end);
                message = "Đã dời giờ nhận xe để xử lý nguy cơ xe về trễ";
            }
            case CANCEL_AND_REFUND -> {
                paymentRequestService.voidUnpaidDepositIfProviderWithdraws(booking.getId());
                refundAmount = createFullProviderCancellationRefund(booking, provider.getUser());
                booking.setCancelledBy(provider.getUser());
                booking.setCancelledAt(now);
                var cancelRisk = vehicleOperationalRiskService.assess(booking);
                booking.setCancelReason(cancelRisk.atRisk()
                        ? CancelReason.PREVIOUS_RENTER_OVERDUE
                        : CancelReason.PROVIDER_NO_VEHICLE);
                booking.setStatus(refundAmount.signum() > 0
                        ? BookingStatus.CANCELLATION_REVIEW
                        : BookingStatus.CANCELLED);
                scheduleService.release(booking.getId());
                driverOptimizationService.recallActiveAssignments(booking.getId());
                message = refundAmount.signum() > 0
                        ? "Đã hủy booking và tạo yêu cầu hoàn 100% số tiền đã nhận"
                        : "Đã hủy booking; chưa phát sinh khoản cần hoàn";
            }
            default -> throw new IllegalArgumentException("Phương án xử lý không hợp lệ");
        }

        if (request.resolution() != OperationalResolutionType.CANCEL_AND_REFUND && booking.getDriver() != null) {
            Long assignedDriverId = booking.getDriver().getId();
            userService.findDriverByIdForUpdate(assignedDriverId).orElseThrow();
            bookingRepository.saveAndFlush(booking);
            boolean eligible = driverOptimizationService.evaluateCandidates(booking.getId(), provider.getId()).stream()
                    .anyMatch(c -> c.driverId().equals(assignedDriverId) && c.eligible());
            if (!eligible) throw new ConflictException("Tài xế hiện tại không đủ điều kiện với xe hoặc lịch mới; cần điều phối lại trước khi thay đổi");
            driverOptimizationService.recallActiveAssignments(booking.getId());
            booking.setDriver(null);
            booking.setDriverConfirmedAt(null);
            booking.setActualArrivedAt(null);
            booking.setAssignmentExpiresAt(null);
            booking.setDriverReplacementRequired(false);
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.saveAndFlush(booking);
            driverOptimizationService.manualAssign(booking.getId(), assignedDriverId, provider.getId());
        }
        booking.setOperationalResolution(request.resolution());
        booking.setOperationalResolutionReason(request.reason().trim());
        booking.setOperationalResolvedAt(now);
        bookingRepository.save(booking);
        bookingNotificationService.changed(booking, "BOOKING_CHANGED");
        return new OperationalResolutionResponse(
                booking.getBookingCode(), booking.getStatus(), request.resolution(),
                booking.getVehicle().getId(), booking.getPickupTime(), refundAmount, message);
    }

    private void requireOperationallyMutable(Booking booking) {
        if (!Set.of(BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED,
                BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED).contains(booking.getStatus())) {
            throw new ConflictException("Chỉ xử lý đổi xe, dời lịch hoặc hủy trước khi chuyến bắt đầu");
        }
    }

    private Integer chargeableKm(BookingPolicySnapshot snapshot) {
        if (snapshot == null || snapshot.getDistanceKm() == null || snapshot.getFreeRadiusKm() == null) {
            return null;
        }
        return (int) Math.ceil(Math.max(0d, snapshot.getDistanceKm().doubleValue() - snapshot.getFreeRadiusKm()));
    }

    private Instant plannedEnd(Booking booking, Instant start) {
        if (booking.getPlannedEndAt() != null)
            return start.plus(Duration.between(booking.getPickupTime(), booking.getPlannedEndAt()));
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElse(null);
        int quantity = bookingChargeRepository.findByBooking_IdAndChargeType(
                        booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        int slotLength = snapshot != null && snapshot.getSlotLengthMinutes() != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        return start.plus(Duration.ofMinutes((long) Math.max(1, quantity) * slotLength));
    }

    private BigDecimal createFullProviderCancellationRefund(Booking booking, User providerUser) {
        BigDecimal refundable = refundEventService.remainingReceived(booking.getId());
        if (refundable.signum() > 0) {
            refundEventService.reserve(booking.getId(), "PROVIDER_CANCEL:" + booking.getId(), refundable,
                    "Hoàn 100% do không thể cung cấp xe: " + booking.getBookingCode(), providerUser);
        }
        return refundable;
    }

    private String determineRentalType(PlanType planType) {
        if (planType == null) return "Theo ngày";
        return switch (planType) {
            case HOURLY -> "Theo giờ";
            case DAILY -> "Theo ngày";
            case MONTHLY -> "Theo tháng";
            case YEARLY -> "Theo năm";
        };
    }

    private String calculateRentalDuration(Booking booking) {
        if (booking.getPlannedEndAt() != null) {
            long hours = Duration.between(booking.getPickupTime(), booking.getPlannedEndAt()).toHours();
            return (hours / 24 > 0 ? hours / 24 + " ngày " : "") + (hours % 24 > 0 ? hours % 24 + " giờ" : "");
        }
        if (booking.getPlan() == null) return "1 ngày";

        if (booking.getPlan().getSlotPrice() != null
                && booking.getPlan().getSlotPrice().compareTo(BigDecimal.ZERO) > 0
                && booking.getTotalAmount() != null) {
            long slots = booking.getTotalAmount()
                    .divideToIntegralValue(booking.getPlan().getSlotPrice())
                    .longValue();
            if (slots < 1) slots = 1;
            return switch (booking.getPlan().getPlanType()) {
                case HOURLY -> slots + " giờ";
                case DAILY -> slots + " ngày";
                case MONTHLY -> slots + " tháng";
                case YEARLY -> slots + " năm";
            };
        }

        int slotLength = booking.getPlan().getSlotLengthMinutes();
        if (booking.getPlan().getPlanType() == PlanType.HOURLY) {
            return Math.max(1, slotLength / 60) + " giờ";
        } else if (booking.getPlan().getPlanType() == PlanType.DAILY) {
            return Math.max(1, slotLength / 1440) + " ngày";
        } else if (booking.getPlan().getPlanType() == PlanType.MONTHLY) {
            return "1 tháng";
        } else {
            return "1 năm";
        }
    }

    private BookingChargeResponse toChargeResponse(BookingCharge c) {
        String createdByName = c.getCreatedBy() != null ? c.getCreatedBy().getFullName() : "Hệ thống";
        return new BookingChargeResponse(
                c.getId(),
                c.getChargeType(),
                c.getDescription(),
                c.getQuantity(),
                c.getUnitPrice(),
                c.getAppliedRate(),
                c.getAmount(),
                c.isWaived(),
                c.getEvidenceUrl(),
                createdByName,
                c.getCreatedAt(),
                c.getConfirmationStatus()
        );
    }

    private List<ProviderBookingDetailResponse.TimelineEventResponse> buildTimeline(Booking booking) {
        List<ProviderBookingDetailResponse.TimelineEventResponse> events = new ArrayList<>();

        if (booking.getCreatedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Tạo yêu cầu đặt xe",
                    "Khách thuê đã gửi yêu cầu đặt xe thành công",
                    booking.getCreatedAt(),
                    "COMPLETED",
                    "check_circle"
            ));
        }

        boolean providerCancelledTrip = booking.getOperationalResolution() == OperationalResolutionType.CANCEL_AND_REFUND
                && booking.getOperationalResolvedAt() != null;
        if (booking.getOperationalResolution() != null && booking.getOperationalResolvedAt() != null) {
            String title = switch (booking.getOperationalResolution()) {
                case CHANGE_VEHICLE -> "Đã đổi xe thay thế";
                case RESCHEDULE -> "Đã dời giờ nhận xe";
                case CANCEL_AND_REFUND -> "Nhà xe đã hủy chuyến";
            };
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    title,
                    booking.getOperationalResolutionReason(),
                    booking.getOperationalResolvedAt(),
                    "COMPLETED",
                    "published_with_changes"
            ));
        }

        if (booking.getProviderConfirmedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Nhà xe chấp nhận đơn",
                    "Nhà cung cấp đã chấp nhận yêu cầu thuê xe",
                    booking.getProviderConfirmedAt(),
                    "COMPLETED",
                    "verified"
            ));
        } else if (booking.getStatus() == BookingStatus.PENDING) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Chờ nhà xe duyệt",
                    "Đang chờ nhà xe duyệt yêu cầu",
                    booking.getCreatedAt().plusSeconds(1800),
                    "CURRENT",
                    "hourglass_top"
            ));
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getStatus() == BookingStatus.COMPLETED) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Khách hoàn tất thanh toán cọc",
                    "Đơn thuê đã được xác nhận và giữ chỗ xe",
                    booking.getCreatedAt().plusSeconds(3600),
                    "COMPLETED",
                    "payments"
            ));
        }

        if (booking.getAssignedAt() != null && booking.getDriver() != null) {
            String driverName = booking.getDriver().getUser() != null ? booking.getDriver().getUser().getFullName() : "Tài xế";
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Phân công tài xế",
                    "Đã phân công tài xế " + driverName + " tiếp nhận chuyến xe",
                    booking.getAssignedAt(),
                    "COMPLETED",
                    "badge"
            ));
        }

        if (booking.getDriverConfirmedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Tài xế nhận chuyến",
                    "Tài xế đã xác nhận tiếp nhận điều hành chuyến xe",
                    booking.getDriverConfirmedAt(),
                    "COMPLETED",
                    "assignment_turned_in"
            ));
        }

        if (booking.getActualArrivedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Tài xế đã đến điểm đón",
                    "Tài xế đã có mặt tại địa điểm đón khách",
                    booking.getActualArrivedAt(),
                    "COMPLETED",
                    "pin_drop"
            ));
        }

        if (booking.getActualStartAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Bắt đầu hành trình",
                    "Tài xế đã đón khách và bắt đầu chuyến đi",
                    booking.getActualStartAt(),
                    "COMPLETED",
                    "directions_car"
            ));
        } else if (booking.getStatus() == BookingStatus.CONFIRMED) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Chuẩn bị khởi hành",
                    "Đang chờ đến giờ đón khách: " + DATE_TIME_FORMATTER.format(booking.getPickupTime()),
                    booking.getPickupTime(),
                    "UPCOMING",
                    "schedule"
            ));
        }

        if (booking.getActualEndAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Hoàn tất chuyến đi",
                    "Chuyến đi đã hoàn tất và kết thúc hành trình",
                    booking.getActualEndAt(),
                    "COMPLETED",
                    "task_alt"
            ));
        }

        if (booking.getStatus() == BookingStatus.REJECTED) {
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Nhà xe từ chối đơn",
                    "Nhà cung cấp đã từ chối yêu cầu thuê xe này",
                    booking.getCancelledAt() != null ? booking.getCancelledAt() : booking.getUpdatedAt(),
                    "ERROR",
                    "cancel"
            ));
        }

        if ((booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.CANCELLATION_REVIEW)
                && !providerCancelledTrip) {
            String reasonText = booking.getCancelReason() == null ? "Yêu cầu hủy" : switch (booking.getCancelReason()) {
                case RENTER_REQUEST -> "Khách thuê yêu cầu hủy";
                case PROVIDER_NO_VEHICLE -> "Nhà cung cấp không còn xe phù hợp";
                case PROVIDER_NO_DRIVER -> "Nhà cung cấp không thể bố trí tài xế";
                case PREVIOUS_RENTER_OVERDUE -> "Xe của chuyến trước chưa về kịp";
                case DEPOSIT_TIMEOUT -> "Quá hạn đặt cọc";
                case YEARLY_REQUEST_TIMEOUT -> "Hết thời gian chờ nhà cung cấp duyệt yêu cầu thuê năm";
                case YEARLY_CONTRACT_TIMEOUT -> "Hết thời gian tải hợp đồng thuê năm";
                case FORCE_MAJEURE -> "Sự kiện bất khả kháng";
                case SYSTEM -> "Hệ thống hủy để bảo đảm an toàn dữ liệu";
            };
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    booking.getStatus() == BookingStatus.CANCELLATION_REVIEW
                            ? "Đang chờ hoàn tiền" : "Đơn thuê đã hủy",
                    "Lý do: " + reasonText,
                    booking.getCancelledAt() != null ? booking.getCancelledAt() : booking.getUpdatedAt(),
                    "CANCELLED",
                    "block"
            ));
        }

        return events;
    }

    @Override
    public TripTrackingResponse getTripTracking(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return bookingTrackingService.buildTrackingResponse(booking);
    }

    @Override
    public TripLocationResponse getTripLocation(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return bookingTrackingService.getLatestLocation(booking.getId());
    }
    private record QueuePage(int offset, int size, Sort sort) implements Pageable {
        public int getPageNumber() { return offset / size; }
        public int getPageSize() { return size; }
        public long getOffset() { return offset; }
        public Sort getSort() { return sort; }
        public Pageable next() { return new QueuePage(Math.addExact(offset, size), size, sort); }
        public Pageable previousOrFirst() { return new QueuePage(Math.max(0, offset - size), size, sort); }
        public Pageable first() { return new QueuePage(0, size, sort); }
        public Pageable withPage(int page) { return new QueuePage(Math.multiplyExact(page, size), size, sort); }
        public boolean hasPrevious() { return offset > 0; }
    }


    private String resolveDriverRejectionReason(Booking booking) {
        if (booking.getStatus() == BookingStatus.REJECTED) {
            return booking.getOperationalResolutionReason();
        }
        return driverAssignmentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(a -> a.getStatus() == com.fptufa26.ecomotion.booking.enums.AssignmentStatus.REJECTED)
                .findFirst()
                .map(a -> a.getRejectionReason() != null
                        ? a.getRejectionReason().name() + (a.getRejectionNote() != null && !a.getRejectionNote().isBlank() ? (": " + a.getRejectionNote().trim()) : "")
                        : a.getRejectionNote())
                .orElse(null);
    }

}
