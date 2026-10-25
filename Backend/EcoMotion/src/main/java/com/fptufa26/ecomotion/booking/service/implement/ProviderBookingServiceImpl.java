package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest;
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
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
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
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.service.UserService;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.entity.VehicleLocation;
import com.fptufa26.ecomotion.booking.repository.VehicleLocationRepository;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderBookingServiceImpl implements ProviderBookingService {

    private static final Logger log = LoggerFactory.getLogger(ProviderBookingServiceImpl.class);

    private static final Duration STALE_LOCATION_THRESHOLD = Duration.ofSeconds(60);

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final MapGeocodingService mapGeocodingService;
    private final PaymentRequestService paymentRequestService;
    private final DisputeService disputeService;
    private final ScheduleService scheduleService;
    private final DriverOptimizationService driverOptimizationService;
    private final TripIncidentService tripIncidentService;
    private final TripEvidenceRepository tripEvidenceRepository;
    private final Clock clock;

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
        return page.map(booking -> toResponse(booking, coverUrls.get(booking.getVehicle().getId())));
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
        long accepted = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.ACCEPTED);
        long confirmed = bookingRepository.countByVehicle_Provider_IdAndStatusIn(providerId, List.of(
                BookingStatus.CONFIRMED,
                BookingStatus.DRIVER_ASSIGNED,
                BookingStatus.DRIVER_CONFIRMED,
                BookingStatus.DRIVER_ARRIVED
        ));
        long inProgress = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.IN_PROGRESS);
        long completed = bookingRepository.countByVehicle_Provider_IdAndStatusIn(providerId, List.of(
                BookingStatus.COMPLETED,
                BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION
        ));
        long rejected = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.REJECTED);
        long cancelled = bookingRepository.countByVehicle_Provider_IdAndStatus(providerId, BookingStatus.CANCELLED);

        return new ProviderBookingSummaryResponse(
                total,
                pending,
                accepted,
                confirmed,
                inProgress,
                completed,
                rejected,
                cancelled
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
                getLatestLocationForBooking(booking.getId())
        );
    }

    @Override
    @Transactional
    public ProviderBookingResponse acceptBooking(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể chấp nhận đơn ở trạng thái Chờ duyệt (PENDING)");
        }

        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId())
                .orElse(null);
        int slots = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        int slotLengthMinutes = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        Instant end = booking.getPickupTime().plus(
                Duration.ofMinutes((long) slotLengthMinutes * Math.max(1, slots)));
        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(
                booking.getVehicle(), booking.getPickupTime(), end);
        if (!scheduleService.isRangeFree(booking.getVehicle().getId(), window.start(), window.end())) {
            throw new ConflictException("Xe không còn trống trong khoảng thời gian đã chọn");
        }

        booking.setStatus(BookingStatus.ACCEPTED);
        Booking saved = bookingRepository.save(booking);

        paymentRequestService.createDepositPaymentRequest(saved);

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(saved.getVehicle().getId()));
        return toResponse(saved, coverUrls.get(saved.getVehicle().getId()));
    }

    @Override
    @Transactional
    public ProviderBookingResponse denyBooking(Long currentUserId, String bookingCodeOrId, String reason) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalArgumentException("Chỉ có thể từ chối đơn ở trạng thái Chờ duyệt (PENDING)");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setCancelledAt(null);
        booking.setCancelledBy(null);
        booking.setCancelReason(null);
        bookingRepository.save(booking);

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(booking.getVehicle().getId()));
        return toResponse(booking, coverUrls.get(booking.getVehicle().getId()));
    }

    @Override
    @Transactional
    public ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId, Long driverId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        Driver driver = driverOptimizationService.manualAssign(booking.getId(), driverId, provider.getId());
        Booking refreshed = bookingRepository.findById(booking.getId()).orElse(booking);

        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(refreshed.getVehicle().getId()));
        return toResponse(refreshed, coverUrls.get(refreshed.getVehicle().getId()));
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
        return driverOptimizationService.suggestDriver(booking.getId(), provider.getId());
    }

    @Override
    @Transactional
    public ProviderBookingResponse autoAssignDriver(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        Driver driver = driverOptimizationService.autoAssign(booking.getId());
        if (driver == null) {
            throw new ConflictException("Không tìm thấy tài xế phù hợp để phân công tự động");
        }

        Booking refreshed = bookingRepository.findById(booking.getId()).orElse(booking);
        Map<Long, String> coverUrls = vehicleService.getCoverImageUrls(Set.of(refreshed.getVehicle().getId()));
        return toResponse(refreshed, coverUrls.get(refreshed.getVehicle().getId()));
    }

    @Override
    @Transactional
    public void resolveIncident(Long currentUserId, String bookingCodeOrId, Long incidentId, ResolveIncidentRequest request) {
        tripIncidentService.resolveIncident(currentUserId, bookingCodeOrId, incidentId, request);
    }

    @Override
    public List<TripIncident> getBookingIncidents(Long currentUserId, String bookingCodeOrId) {
        return tripIncidentService.getBookingIncidentsForProvider(currentUserId, bookingCodeOrId);
    }

    @Override
    public List<TripEvidence> getBookingEvidence(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return tripEvidenceRepository.findByBooking_IdOrderByRecordedAtAsc(booking.getId());
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
        if (request.chargeType() == ChargeType.BASE_RENTAL || request.chargeType() == ChargeType.DEPOSIT) {
            throw new ConflictException("Không thể thêm thủ công tiền thuê gốc hoặc tiền cọc");
        }

        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(request.chargeType());
        charge.setDescription(request.description());
        charge.setQuantity(request.quantity());
        charge.setUnitPrice(request.unitPrice());
        charge.setAppliedRate(request.appliedRate());
        charge.setAmount(request.amount());
        charge.setCreatedBy(provider.getUser());
        charge.setWaived(false);
        charge.setEvidenceUrl(request.evidenceUrl());

        BookingCharge saved = bookingChargeRepository.save(charge);

        return toChargeResponse(saved);
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
        if (booking.getProviderConfirmedAt() == null || booking.getRenterConfirmedAt() == null) {
            throw new ConflictException("Cần xác nhận kết thúc từ cả tài xế và người thuê trước khi chốt quyết toán");
        }
        if (disputeService.hasActiveDispute(booking.getId())) {
            throw new ConflictException("Không thể chốt quyết toán khi đơn thuê đang có tranh chấp");
        }

        BigDecimal remaining = paymentRequestService.calculateRemainingAmount(booking.getId());
        if (remaining.signum() <= 0) {
            booking.setStatus(BookingStatus.COMPLETED);
            bookingRepository.save(booking);
            return new SettlementResultResponse(
                    booking.getBookingCode(), BookingStatus.COMPLETED, null, BigDecimal.ZERO.setScale(2));
        }

        PaymentRequest paymentRequest = paymentRequestService.createFinalBalancePaymentRequest(booking.getId());
        booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        bookingRepository.save(booking);
        return new SettlementResultResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                paymentRequest.getId(),
                paymentRequest.getAmount().subtract(paymentRequest.getPaidAmount()).max(BigDecimal.ZERO));
    }

    @Override
    public List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        List<Driver> drivers = userService.findDriversByProviderIdAndStatus(provider.getId(), DriverStatus.APPROVED);

        List<BookingStatus> activeStatuses = List.of(BookingStatus.ACCEPTED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

        return drivers.stream().map(driver -> {
            long activeBookings = bookingRepository.countByDriver_IdAndStatusIn(driver.getId(), activeStatuses);
            String fullName = driver.getUser() != null ? driver.getUser().getFullName() : null;
            String phone = driver.getUser() != null ? driver.getUser().getPhone() : null;

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
                    driver.getRatingAvg(),
                    driver.getRatingCount(),
                    activeBookings
            );
        }).toList();
    }

    @Override
    public byte[] exportBookingsCsv(Long currentUserId, String status) {
        List<ProviderBookingResponse> bookings = listBookings(currentUserId, status);

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

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Provider resolveProvider(Long currentUserId) {
        return userService.findProviderProfile(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Current user has no provider profile"));
    }

    private Booking resolveBooking(Provider provider, String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new BookingNotFoundException("Mã đơn thuê không hợp lệ");
        }

        try {
            Long id = Long.parseLong(bookingCodeOrId.trim());
            return bookingRepository.findByIdAndVehicle_Provider_Id(id, provider.getId())
                    .orElseGet(() -> bookingRepository.findByBookingCodeAndVehicle_Provider_Id(bookingCodeOrId.trim(), provider.getId())
                            .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingCodeOrId)));
        } catch (NumberFormatException ex) {
            return bookingRepository.findByBookingCodeAndVehicle_Provider_Id(bookingCodeOrId.trim(), provider.getId())
                    .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + bookingCodeOrId));
        }
    }

    private Booking resolveBookingForUpdate(Provider provider, String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new BookingNotFoundException("Mã đơn thuê không hợp lệ");
        }
        String value = bookingCodeOrId.trim();
        try {
            return bookingRepository.findByIdAndProviderIdForUpdate(Long.parseLong(value), provider.getId())
                    .orElseGet(() -> bookingRepository.findByBookingCodeAndProviderIdForUpdate(value, provider.getId())
                            .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + value)));
        } catch (NumberFormatException ex) {
            return bookingRepository.findByBookingCodeAndProviderIdForUpdate(value, provider.getId())
                    .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê: " + value));
        }
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
        return bookings.stream()
                .map(b -> toResponse(b, coverUrls.get(b.getVehicle().getId())))
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
                booking.getActualEndAt()
        );
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
                c.getCreatedAt()
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

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            String reasonText = booking.getCancelReason() != null ? booking.getCancelReason().name() : "Yêu cầu hủy";
            events.add(new ProviderBookingDetailResponse.TimelineEventResponse(
                    "Đơn thuê đã hủy",
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
        return buildTrackingResponse(booking);
    }

    @Override
    public TripLocationResponse getTripLocation(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);
        return getLatestLocationForBooking(booking.getId());
    }

    private TripTrackingResponse buildTrackingResponse(Booking booking) {
        TripLocationResponse locationResponse = getLatestLocationForBooking(booking.getId());

        Double pickupLat = booking.getPickupLatitude() == null ? null : booking.getPickupLatitude().doubleValue();
        Double pickupLon = booking.getPickupLongitude() == null ? null : booking.getPickupLongitude().doubleValue();
        if (pickupLat == null && pickupLon == null
                && booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()) {
            Optional<MapGeocodingService.GeoPoint> geoOpt = mapGeocodingService.geocodeAddress(booking.getPickupAddress());
            if (geoOpt.isPresent()) {
                pickupLat = geoOpt.get().latitude();
                pickupLon = geoOpt.get().longitude();
            }
        }

        TripTrackingResponse.DriverSummary driverSummary = null;
        if (booking.getDriver() != null) {
            Driver d = booking.getDriver();
            String name = (d.getUser() != null) ? d.getUser().getFullName() : null;
            String phone = (d.getUser() != null) ? d.getUser().getPhone() : null;
            driverSummary = new TripTrackingResponse.DriverSummary(
                    d.getId(),
                    name,
                    phone,
                    d.getLicenseNo(),
                    d.getPortraitUrl()
            );
        }

        TripTrackingResponse.VehicleSummary vehicleSummary = null;
        if (booking.getVehicle() != null) {
            Vehicle v = booking.getVehicle();
            String vName = ((v.getBrand() != null ? v.getBrand() : "") + " " + (v.getModel() != null ? v.getModel() : "")).trim();
            vehicleSummary = new TripTrackingResponse.VehicleSummary(
                    v.getId(),
                    vName,
                    v.getBrand(),
                    v.getModel(),
                    v.getLicensePlate()
            );
        }

        String executionStatus = DriverTripServiceImpl.deriveExecutionStatus(booking);

        return new TripTrackingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                executionStatus,
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getPickupTime(),
                booking.getPickupAddress(),
                pickupLat,
                pickupLon,
                locationResponse,
                driverSummary,
                vehicleSummary
        );
    }

    private TripLocationResponse getLatestLocationForBooking(Long bookingId) {
        Instant now = clock.instant();
        return vehicleLocationRepository.findFirstByBooking_IdOrderByReceivedAtDesc(bookingId)
                .map(loc -> {
                    boolean isStale = Duration.between(loc.getReceivedAt(), now).compareTo(STALE_LOCATION_THRESHOLD) > 0;
                    String state = isStale ? "STALE" : "LIVE";
                    return new TripLocationResponse(
                            loc.getLatitude(),
                            loc.getLongitude(),
                            loc.getAccuracyMeters(),
                            loc.getRecordedAt(),
                            loc.getReceivedAt(),
                            isStale,
                            state
                    );
                })
                .orElse(new TripLocationResponse(null, null, null, null, null, false, "UNAVAILABLE"));
    }
}
