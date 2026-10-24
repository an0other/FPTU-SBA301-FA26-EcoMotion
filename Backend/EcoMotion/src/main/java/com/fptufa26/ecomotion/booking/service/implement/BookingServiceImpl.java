package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.booking.dto.request.UpdateBookingRequestDetails;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingRequestDetailsResponse;
import com.fptufa26.ecomotion.booking.dto.response.CancelBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmEndResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.RefundPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.TimelineStepResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.mapper.BookingMapper;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.booking.service.DepositRefundRules;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Buffer time (minutes) before/after each booking for vehicle preparation */
    private static final Set<BookingStatus> BLOCKING = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED,
            BookingStatus.DRIVER_CONFIRMED,
            BookingStatus.DRIVER_ARRIVED,
            BookingStatus.IN_PROGRESS);

    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository snapshotRepository;
    private final BookingChargeRepository chargeRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingMapper bookingMapper;
    private final UserService userService;
    private final VehicleCatalogService vehicleCatalogService;
    private final ScheduleService scheduleService;
    private final DisputeService disputeService;
    private final PaymentRequestRepository paymentRequestRepository;

    private static final Set<BookingStatus> CANCELLABLE = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.CONFIRMED);
    private static final Set<BookingStatus> CHARGE_DISPUTE = EnumSet.of(
            BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION,
            BookingStatus.AWAITING_FINAL_PAYMENT);

    @Override
    @Transactional
    public CreateBookingResponse create(Long userId, CreateBookingRequest request) {
        Renter renter = userService.requireRenterByUserId(userId);
        Vehicle vehicle = vehicleCatalogService.requireApproved(request.vehicleId());
        RentalPlan plan = vehicleCatalogService.requireActivePlan(request.vehicleId(), request.planId());

        if (plan.getPlanType() == PlanType.YEARLY) {
            throw new ConflictException("Yearly plans must use the dedicated contract flow");
        }

        String driverNote = normalizeDriverNote(request.driverNote());

        int passengerLimit = plan.getPlanType() == PlanType.YEARLY
                ? vehicle.getSeatCount()
                : vehicle.getSeatCount() - 1;
        if (passengerLimit < 1 || request.passengerCount() > passengerLimit) {
            throw new ConflictException("Passenger count exceeds seat count");
        }

        Instant start = request.pickupTime();
        if (!start.isAfter(Instant.now())) {
            throw new ConflictException("Pickup time must be in the future");
        }
        Instant end = start.plus(Duration.ofMinutes((long) plan.getSlotLengthMinutes() * request.slotCount()));
        if (!end.isAfter(start)) {
            throw new ConflictException("Rental range is empty");
        }
        // Apply buffer for vehicle preparation time between bookings
        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(vehicle, start, end);
        if (!scheduleService.isRangeFree(vehicle.getId(), window.start(), window.end())) {
            throw new ConflictException("Vehicle is not free in the selected range (including maintenance buffer)");
        }
        if (renterOverlaps(renter.getId(), start, end)) {
            throw new ConflictException("Renter already has a booking in the selected range");
        }

        BigDecimal total = plan.getSlotPrice().multiply(BigDecimal.valueOf(request.slotCount()));
        User actor = renter.getUser();

        Booking booking = new Booking();
        booking.setBookingCode(nextBookingCode());
        booking.setRenter(renter);
        booking.setVehicle(vehicle);
        booking.setPlan(plan);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPickupAddress(request.pickupAddress().trim());
        booking.setPickupLatitude(request.pickupLatitude());
        booking.setPickupLongitude(request.pickupLongitude());
        booking.setDriverNote(driverNote);
        booking.setPickupTime(start);
        booking.setPassengerCount(request.passengerCount());
        booking.setTotalAmount(total);
        bookingRepository.save(booking);

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(plan.getSlotPrice());
        snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
        snapshot.setPlanType(plan.getPlanType());
        snapshot.setDepositRatio(plan.getPlanType() == PlanType.YEARLY ? null : vehicle.getProvider().getDepositRatio());
        snapshot.setUpfrontAmount(plan.getUpfrontAmount());
        snapshot.setAllowedOvertimeMinutes(plan.getAllowedOvertimeMinutes());
        snapshot.setOvertimeSurchargeRatio(plan.getOvertimeSurchargeRatio());
        snapshot.setWaitingFreeMinutes(plan.getWaitingFreeMinutes());
        snapshot.setWaitingRate(plan.getWaitingRate());
        snapshot.setWeekendSurchargeRatio(plan.getWeekendSurchargeRatio());
        snapshot.setFreeRadiusKm(plan.getFreeRadiusKm());
        snapshot.setOutOfRadiusRate(plan.getOutOfRadiusRate());
        snapshot.setHourlyToDailyThresholdMinutes(plan.getHourlyToDailyThresholdMinutes());
        snapshotRepository.save(snapshot);

        BookingCharge charge = new BookingCharge();
        charge.setBooking(booking);
        charge.setChargeType(ChargeType.BASE_RENTAL);
        charge.setDescription("Thuê " + request.slotCount() + " slot");
        charge.setQuantity(request.slotCount());
        charge.setUnitPrice(plan.getSlotPrice());
        charge.setAmount(total);
        charge.setCreatedBy(actor);
        charge.setWaived(false);
        chargeRepository.save(charge);

        return bookingMapper.toCreateBookingResponse(booking, end, charge);
    }

    private String normalizeDriverNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 160) {
            throw new BadRequestException("Driver note cannot exceed 160 characters");
        }
        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> listMine(Long userId, BookingStatus status) {
        Renter renter = userService.requireRenterByUserId(userId);
        List<Booking> bookings = status == null
                ? bookingRepository.findByRenter_IdOrderByPickupTimeDesc(renter.getId())
                : bookingRepository.findByRenter_IdAndStatusOrderByPickupTimeDesc(renter.getId(), status);
        return bookings.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode) {
        throw new ConflictException("Checkout cũ đã ngừng; vui lòng thanh toán đặt cọc qua VNPAY Sandbox");
    }

    @Override
    @Transactional
    public CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request) {
        throw new ConflictException("Checkout cũ đã ngừng; vui lòng thanh toán đặt cọc qua VNPAY Sandbox");
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse detail(Long userId, String bookingCode) {
        Booking booking = requireOwned(userId, bookingCode);
        return toDetail(booking);
    }

    @Override
    @Transactional
    public BookingRequestDetailsResponse updateRequestDetails(
            Long userId, String bookingCode, UpdateBookingRequestDetails request) {
        Booking booking = bookingRepository.findByBookingCodeAndRenterUserIdForUpdate(bookingCode, userId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!canEditRequest(booking)) {
            throw new ConflictException("Booking request details can no longer be edited");
        }
        if (!request.pickupTime().isAfter(Instant.now())) {
            throw new ConflictException("Pickup time must be in the future");
        }
        int passengerLimit = Math.max(0, booking.getVehicle().getSeatCount() - 1);
        if (request.passengerCount() > passengerLimit) {
            throw new ConflictException("Passenger count exceeds seat count");
        }

        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        Instant end = request.pickupTime().plus(
                Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(
                booking.getVehicle(), request.pickupTime(), end);
        if (!scheduleService.isRangeFree(booking.getVehicle().getId(), window.start(), window.end())) {
            throw new ConflictException("Vehicle is not free in the selected range (including maintenance buffer)");
        }
        if (renterOverlaps(booking.getRenter().getId(), request.pickupTime(), end, booking.getId())) {
            throw new ConflictException("Renter already has a booking in the selected range");
        }

        booking.setPickupTime(request.pickupTime());
        booking.setPickupAddress(request.pickupAddress().trim());
        booking.setPickupLatitude(request.pickupLatitude());
        booking.setPickupLongitude(request.pickupLongitude());
        booking.setPassengerCount(request.passengerCount());
        booking.setDriverNote(normalizeDriverNote(request.driverNote()));

        return new BookingRequestDetailsResponse(
                booking.getBookingCode(), booking.getPickupTime(), end,
                booking.getPickupAddress(), booking.getPickupLatitude(), booking.getPickupLongitude(),
                booking.getPassengerCount(), booking.getDriverNote(), CANCELLABLE.contains(booking.getStatus()),
                true, canPay(booking));
    }

    @Override
    @Transactional
    public CancelBookingResponse cancel(Long userId, String bookingCode) {
        Booking booking = requireOwned(userId, bookingCode);
        if (!CANCELLABLE.contains(booking.getStatus())) {
            throw new ConflictException("Booking cannot be cancelled");
        }
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        Instant cancelledAt = Instant.now();
        BigDecimal refundAmount = refundAmount(booking, snapshot, cancelledAt);
        if (refundAmount.signum() > 0) {
            saveRefund(booking, refundAmount, cancelledAt);
        }
        booking.setCancelledBy(booking.getRenter().getUser());
        booking.setCancelReason(CancelReason.RENTER_REQUEST);
        booking.setCancelledAt(cancelledAt);
        booking.setStatus(BookingStatus.CANCELLED);
        scheduleService.release(booking.getId());
        return bookingMapper.toCancel(booking, refundAmount);
    }

    @Override
    @Transactional
    public ConfirmEndResponse confirmEnd(Long userId, String bookingCode) {
        Booking booking = requireOwned(userId, bookingCode);
        if (booking.getStatus() != BookingStatus.IN_PROGRESS
                && booking.getStatus() != BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION) {
            throw new ConflictException("Booking is not in progress or awaiting settlement");
        }
        if (booking.getRenterConfirmedAt() != null) {
            throw new ConflictException("Renter already confirmed the end");
        }
        booking.setRenterConfirmedAt(Instant.now());
        if (booking.getProviderConfirmedAt() != null) {
            booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        }
        return bookingMapper.toConfirmEnd(booking);
    }

    @Override
    @Transactional
    public DisputeOpenedResponse openDispute(Long userId, String bookingCode, OpenDisputeRequest request) {
        Booking booking = bookingRepository.findByBookingCodeAndRenterUserIdForUpdate(bookingCode, userId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        BookingCharge charge = null;
        if (request.category() == DisputeCategory.CHARGE) {
            if (request.chargeType() == null) {
                throw new BadRequestException("chargeType is required");
            }
            if (request.chargeType() == ChargeType.BASE_RENTAL || request.chargeType() == ChargeType.DEPOSIT) {
                throw new BadRequestException("Only supplemental charges can be disputed as a charge");
            }
            List<BookingCharge> matches = chargeRepository.findAllByBooking_IdAndChargeType(
                    booking.getId(), request.chargeType());
            if (matches.isEmpty()) {
                throw new NotFoundException("Charge not found");
            }
            if (matches.size() > 1) {
                throw new ConflictException("More than one charge of this type");
            }
            charge = matches.getFirst();
        } else if (request.category() == DisputeCategory.DEPOSIT_REFUND
                && chargeRepository.findAllByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT).isEmpty()) {
            throw new ConflictException("No deposit to dispute");
        }
        requireDisputeAllowed(booking, request.category());
        PaymentRequest finalRequest = null;
        if (request.category() == DisputeCategory.CHARGE) {
            finalRequest = paymentRequestRepository
                    .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                    .orElse(null);
            if (finalRequest != null && (finalRequest.getStatus() == PaymentRequestStatus.CONFIRMED
                    || finalRequest.getPaidAmount().signum() > 0)) {
                throw new ConflictException("Final payment has already been confirmed");
            }
        }
        DisputeOpenedResponse response = disputeService.open(
                booking, booking.getRenter().getUser(), request.category(), charge);
        if (finalRequest != null) {
            finalRequest.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            finalRequest.setIssueNote("Payment held while a charge dispute is reviewed");
        }
        return response;
    }

    private BookingDetailResponse toDetail(Booking booking) {
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        List<BookingCharge> chargeRows = chargeRepository.findByBooking_IdOrderByIdAsc(booking.getId());
        List<BookingChargeLineResponse> charges = chargeRows.stream().map(bookingMapper::toChargeLine).toList();
        List<PaymentTransaction> payments = paymentTransactionRepository.findByBooking_Id(booking.getId());
        RefundPreviewResponse refundPreview = refundPreview(booking, snapshot, chargeRows, payments, Instant.now());
        boolean canConfirm = (booking.getStatus() == BookingStatus.IN_PROGRESS
                || booking.getStatus() == BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION)
                && booking.getRenterConfirmedAt() == null;
        boolean canEdit = canEditRequest(booking);
        return bookingMapper.toDetail(
                booking,
                snapshot.getPlanType(),
                vehicleCatalogService.coverImageUrl(booking.getVehicle().getId()),
                endOf(booking, snapshot, slots),
                slots,
                charges,
                timeline(booking, payments),
                refundPreview,
                CANCELLABLE.contains(booking.getStatus()),
                canEdit,
                canPay(booking),
                canConfirm,
                disputeCategories(booking, chargeRows));
    }

    private void requireDisputeAllowed(Booking booking, DisputeCategory category) {
        boolean allowed = switch (category) {
            case CHARGE -> CHARGE_DISPUTE.contains(booking.getStatus());
            case SERVICE_QUALITY -> booking.getStatus() == BookingStatus.IN_PROGRESS
                    || booking.getStatus() == BookingStatus.COMPLETED;
            case DEPOSIT_REFUND -> booking.getStatus() == BookingStatus.CANCELLED;
            case TRIP_NOT_CONFIRMED -> booking.getStatus() == BookingStatus.IN_PROGRESS
                    && (booking.getRenterConfirmedAt() != null) != (booking.getProviderConfirmedAt() != null);
        };
        if (!allowed) {
            throw new ConflictException("Dispute is not available for this booking");
        }
    }

    private List<DisputeCategory> disputeCategories(Booking booking, List<BookingCharge> charges) {
        List<DisputeCategory> categories = new ArrayList<>();
        if (CHARGE_DISPUTE.contains(booking.getStatus()) && !charges.isEmpty()) {
            categories.add(DisputeCategory.CHARGE);
        }
        if (booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getStatus() == BookingStatus.COMPLETED) {
            categories.add(DisputeCategory.SERVICE_QUALITY);
        }
        boolean hasDeposit = charges.stream().anyMatch(charge -> charge.getChargeType() == ChargeType.DEPOSIT);
        if (booking.getStatus() == BookingStatus.CANCELLED && hasDeposit) {
            categories.add(DisputeCategory.DEPOSIT_REFUND);
        }
        if (booking.getStatus() == BookingStatus.IN_PROGRESS
                && (booking.getRenterConfirmedAt() != null) != (booking.getProviderConfirmedAt() != null)) {
            categories.add(DisputeCategory.TRIP_NOT_CONFIRMED);
        }
        return categories;
    }

    private RefundPreviewResponse refundPreview(Booking booking,
                                                BookingPolicySnapshot snapshot,
                                                List<BookingCharge> charges,
                                                List<PaymentTransaction> payments,
                                                Instant at) {
        if (!CANCELLABLE.contains(booking.getStatus())) {
            return new RefundPreviewResponse(false, null, BigDecimal.ZERO, "Đơn này không huỷ được");
        }
        if (snapshot.getPlanType() == PlanType.YEARLY) {
            return new RefundPreviewResponse(true, null, BigDecimal.ZERO, "Trả trước không hoàn theo chính sách gói năm");
        }
        BigDecimal paidDeposit = paidDeposit(charges, payments);
        if (paidDeposit == null) {
            return new RefundPreviewResponse(true, null, BigDecimal.ZERO, "Chưa thu cọc, số hoàn là 0");
        }
        BigDecimal ratio = DepositRefundRules.ratio(snapshot.getPlanType(), at, booking.getPickupTime());
        BigDecimal amount = paidDeposit.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        String explanation = ratio.compareTo(DepositRefundRules.FULL) == 0
                ? "Huỷ trước mốc, hoàn 100% cọc"
                : "Huỷ sau mốc, hoàn 50% cọc. Phần còn lại thuộc nhà cung cấp";
        return new RefundPreviewResponse(true, ratio, amount, explanation);
    }

    private BigDecimal refundAmount(Booking booking, BookingPolicySnapshot snapshot, Instant cancelledAt) {
        if (snapshot.getPlanType() == PlanType.YEARLY) {
            return BigDecimal.ZERO;
        }
        List<BookingCharge> charges = chargeRepository.findByBooking_IdOrderByIdAsc(booking.getId());
        List<PaymentTransaction> payments = paymentTransactionRepository.findByBooking_Id(booking.getId());
        BigDecimal paidDeposit = paidDeposit(charges, payments);
        if (paidDeposit == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = DepositRefundRules.ratio(snapshot.getPlanType(), cancelledAt, booking.getPickupTime());
        return paidDeposit.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal paidDeposit(List<BookingCharge> charges, List<PaymentTransaction> payments) {
        BookingCharge deposit = charges.stream()
                .filter(charge -> charge.getChargeType() == ChargeType.DEPOSIT)
                .findFirst()
                .orElse(null);
        if (deposit == null) {
            return null;
        }
        boolean paid = payments.stream().anyMatch(payment ->
                payment.getType() == PaymentType.DEPOSIT_PAY && payment.getStatus() == PaymentStatus.PAID);
        return paid ? deposit.getAmount() : null;
    }

    private void saveRefund(Booking booking, BigDecimal amount, Instant paidAt) {
        PaymentTransaction depositPayment = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                .filter(payment -> payment.getType() == PaymentType.DEPOSIT_PAY && payment.getStatus() == PaymentStatus.PAID)
                .findFirst()
                .orElseThrow(() -> new ConflictException("Paid deposit not found"));
        BookingCharge deposit = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT)
                .orElseThrow(() -> new ConflictException("Deposit charge not found"));
        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setCharge(deposit);
        refund.setType(PaymentType.REFUND);
        refund.setAmount(amount);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(depositPayment.getMethod());
        refund.setStatus(PaymentStatus.PAID);
        refund.setPaidAt(paidAt);
        paymentTransactionRepository.save(refund);
    }

    private List<TimelineStepResponse> timeline(Booking booking, List<PaymentTransaction> payments) {
        List<TimelineStepResponse> steps = new ArrayList<>();
        steps.add(new TimelineStepResponse("Đã gửi yêu cầu", booking.getCreatedAt(), null, true, false));
        if (booking.getStatus() == BookingStatus.REJECTED) {
            steps.add(new TimelineStepResponse("Chủ xe đã từ chối", null, "Không có lý do", true, true));
        } else if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CANCELLED) {
            steps.add(new TimelineStepResponse("Chủ xe đã chấp nhận", null, null, true, false));
        }
        payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID
                        && (payment.getType() == PaymentType.DEPOSIT_PAY || payment.getType() == PaymentType.UPFRONT_PAY))
                .findFirst()
                .ifPresent(payment -> steps.add(new TimelineStepResponse(
                        payment.getType() == PaymentType.UPFRONT_PAY ? "Đã thanh toán trả trước" : "Đã thanh toán cọc",
                        payment.getPaidAt(),
                        null,
                        true,
                        false)));
        if (booking.getActualStartAt() != null) {
            steps.add(new TimelineStepResponse("Đang chạy", booking.getActualStartAt(), null, true, false));
        }
        if (booking.getActualEndAt() != null) {
            steps.add(new TimelineStepResponse("Kết thúc thực tế", booking.getActualEndAt(), null, true, false));
        }
        if (booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getRenterConfirmedAt() != null) {
            steps.add(new TimelineStepResponse(
                    "Bạn đã xác nhận kết thúc",
                    booking.getRenterConfirmedAt(),
                    null,
                    booking.getRenterConfirmedAt() != null,
                    false));
            steps.add(new TimelineStepResponse(
                    "Phía nhà cung cấp đã xác nhận",
                    booking.getProviderConfirmedAt(),
                    null,
                    booking.getProviderConfirmedAt() != null,
                    false));
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            steps.add(new TimelineStepResponse("Hoàn thành", null, null, true, false));
        }
        if (booking.getCancelledAt() != null) {
            steps.add(new TimelineStepResponse(
                    "Đã huỷ",
                    booking.getCancelledAt(),
                    cancelLabel(booking.getCancelReason()),
                    true,
                    true));
        }
        return steps;
    }

    private String cancelLabel(CancelReason reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case RENTER_REQUEST -> "Khách tự huỷ";
            case PROVIDER_NO_VEHICLE -> "Nhà cung cấp hết xe";
            case PROVIDER_NO_DRIVER -> "Nhà cung cấp hết tài xế";
            case PREVIOUS_RENTER_OVERDUE -> "Khách của chuyến trước chưa trả xe";
            case FORCE_MAJEURE -> "Bất khả kháng";
            case SYSTEM -> "Hệ thống huỷ";
        };
    }

    private BookingSummaryResponse toSummary(Booking booking) {
        PlanType planType = snapshotRepository.findByBooking_Id(booking.getId())
                .map(BookingPolicySnapshot::getPlanType)
                .orElseGet(() -> booking.getPlan().getPlanType());
        String imageUrl = vehicleCatalogService.coverImageUrl(booking.getVehicle().getId());
        return bookingMapper.toSummary(booking, planType, imageUrl, canPay(booking));
    }

    private Booking requireOwned(Long userId, String bookingCode) {
        Optional<Booking> opt = bookingRepository.findByBookingCode(bookingCode);
        if (opt.isEmpty()) {
            try {
                Long id = Long.parseLong(bookingCode);
                opt = bookingRepository.findById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        Booking booking = opt.orElseThrow(() -> new NotFoundException("Booking not found"));
        Long ownerId = booking.getRenter().getUser().getUserId();
        if (!ownerId.equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập đơn thuê này");
        }
        return booking;
    }

    private Booking requireAccepted(Long userId, String bookingCode) {
        Booking booking = requireOwned(userId, bookingCode);
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new ConflictException("Booking is not accepted");
        }
        return booking;
    }

    private BookingPolicySnapshot requireSnapshot(Booking booking) {
        return snapshotRepository.findByBooking_Id(booking.getId())
                .orElseGet(() -> createFallbackSnapshot(booking));
    }

    private BookingPolicySnapshot createFallbackSnapshot(Booking booking) {
        RentalPlan plan = booking.getPlan();
        Provider provider = (booking.getVehicle() != null) ? booking.getVehicle().getProvider() : null;
        BigDecimal depositRatio = (provider != null && provider.getDepositRatio() != null)
                ? provider.getDepositRatio()
                : new BigDecimal("0.30");

        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        if (plan != null) {
            snapshot.setSlotPrice(plan.getSlotPrice());
            snapshot.setSlotLengthMinutes(plan.getSlotLengthMinutes());
            snapshot.setPlanType(plan.getPlanType());
            snapshot.setDepositRatio(plan.getPlanType() == PlanType.YEARLY ? null : depositRatio);
            snapshot.setUpfrontAmount(plan.getUpfrontAmount());
            snapshot.setAllowedOvertimeMinutes(plan.getAllowedOvertimeMinutes());
            snapshot.setOvertimeSurchargeRatio(plan.getOvertimeSurchargeRatio());
            snapshot.setWaitingFreeMinutes(plan.getWaitingFreeMinutes());
            snapshot.setWaitingRate(plan.getWaitingRate());
            snapshot.setWeekendSurchargeRatio(plan.getWeekendSurchargeRatio());
            snapshot.setFreeRadiusKm(plan.getFreeRadiusKm());
            snapshot.setOutOfRadiusRate(plan.getOutOfRadiusRate());
            snapshot.setHourlyToDailyThresholdMinutes(plan.getHourlyToDailyThresholdMinutes());
        } else {
            BigDecimal price = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
            snapshot.setSlotPrice(price);
            snapshot.setSlotLengthMinutes(1440);
            snapshot.setPlanType(PlanType.DAILY);
            snapshot.setDepositRatio(depositRatio);
        }
        return snapshotRepository.save(snapshot);
    }

    private int baseSlots(Booking booking) {
        return chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElseGet(() -> {
                    RentalPlan plan = booking.getPlan();
                    if (plan != null && plan.getSlotPrice() != null && plan.getSlotPrice().compareTo(BigDecimal.ZERO) > 0
                            && booking.getTotalAmount() != null) {
                        try {
                            int calculated = booking.getTotalAmount().divideToIntegralValue(plan.getSlotPrice()).intValue();
                            return Math.max(calculated, 1);
                        } catch (Exception ignored) {
                            return 1;
                        }
                    }
                    return 1;
                });
    }

    private Instant endOf(Booking booking, BookingPolicySnapshot snapshot, int slots) {
        return booking.getPickupTime().plus(Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
    }

    private BigDecimal amountDue(Booking booking, BookingPolicySnapshot snapshot) {
        if (snapshot.getPlanType() == PlanType.YEARLY) {
            return snapshot.getUpfrontAmount();
        }
        return booking.getTotalAmount()
                .multiply(snapshot.getDepositRatio())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean renterOverlaps(Long renterId, Instant start, Instant end) {
        return renterOverlaps(renterId, start, end, null);
    }

    private boolean renterOverlaps(Long renterId, Instant start, Instant end, Long excludeBookingId) {
        List<Booking> existing = bookingRepository.findByRenter_IdAndStatusIn(renterId, BLOCKING);
        for (Booking booking : existing) {
            if (excludeBookingId != null && excludeBookingId.equals(booking.getId())) {
                continue;
            }
            Instant otherEnd = endOf(booking);
            if (start.isBefore(otherEnd) && booking.getPickupTime().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private boolean canEditRequest(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.ACCEPTED) {
            return false;
        }
        return paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT)
                .stream()
                .flatMap(request -> request.getEvidences().stream())
                .noneMatch(evidence -> evidence.getStatus() == PaymentEvidenceStatus.PENDING
                        || evidence.getStatus() == PaymentEvidenceStatus.ACCEPTED);
    }

    private boolean canPay(Booking booking) {
        if (booking.getStatus() != BookingStatus.ACCEPTED
                && booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
            return false;
        }

        if (booking.getStatus() == BookingStatus.ACCEPTED) {
            boolean alreadyPaid = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                    .anyMatch(tx -> tx.getType() == PaymentType.DEPOSIT_PAY && tx.getStatus() == PaymentStatus.PAID);
            if (alreadyPaid) {
                return false;
            }
            Optional<PaymentRequest> depositReqOpt = paymentRequestRepository
                    .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT);
            if (depositReqOpt.isPresent()) {
                PaymentRequest request = depositReqOpt.get();
                if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now())) {
                    return false;
                }
                return request.getStatus() == PaymentRequestStatus.PENDING
                        || request.getStatus() == PaymentRequestStatus.REJECTED;
            }
            if (booking.getPickupTime() != null && booking.getPickupTime().isBefore(Instant.now())) {
                return false;
            }
            return true;
        }

        // AWAITING_FINAL_PAYMENT
        boolean alreadySettled = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                .anyMatch(tx -> tx.getType() == PaymentType.SETTLEMENT && tx.getStatus() == PaymentStatus.PAID);
        if (alreadySettled) {
            return false;
        }
        Optional<PaymentRequest> finalReqOpt = paymentRequestRepository
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE);
        if (finalReqOpt.isPresent()) {
            PaymentRequest request = finalReqOpt.get();
            if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now())) {
                return false;
            }
            return request.getStatus() == PaymentRequestStatus.PENDING
                    || request.getStatus() == PaymentRequestStatus.REJECTED;
        }
        return true;
    }

    private Instant endOf(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (snapshot == null) {
            return booking.getPickupTime();
        }
        int slots = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
    }

    private String nextBookingCode() {
        int year = Year.now(ZONE).getValue();
        String prefix = "EM-" + year + "-";
        long sequence = bookingRepository.countByBookingCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    // ---------------------------------------------------------
    // Admin Operations Methods (TV6 - A04)
    // ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<Booking> listAdminBookings(BookingStatus status, String keyword) {
        List<Booking> bookings = (status != null)
                ? bookingRepository.findByStatusOrderByCreatedAtDesc(status)
                : bookingRepository.findAllByOrderByCreatedAtDesc();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            return bookings.stream()
                    .filter(b -> (b.getBookingCode() != null && b.getBookingCode().toLowerCase(Locale.ROOT).contains(lower))
                            || (b.getPickupAddress() != null && b.getPickupAddress().toLowerCase(Locale.ROOT).contains(lower))
                            || (b.getVehicle() != null && (b.getVehicle().getLicensePlate().toLowerCase(Locale.ROOT).contains(lower)
                                || b.getVehicle().getModel().toLowerCase(Locale.ROOT).contains(lower))))
                    .toList();
        }
        return bookings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingCharge> getChargesForBooking(Long bookingId) {
        return chargeRepository.findByBooking_Id(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> listTransactions(PaymentStatus status, PaymentType type) {
        if (status != null) {
            return paymentTransactionRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        if (type != null) {
            return paymentTransactionRepository.findByTypeOrderByCreatedAtDesc(type);
        }
        return paymentTransactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransaction> getTransactionsForBooking(Long bookingId) {
        return paymentTransactionRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
    }

    @Override
    @Transactional
    public PaymentTransaction processMockRefund(Long bookingId, BigDecimal amount, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn thuê không tồn tại: " + bookingId));

        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setAmount(amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : booking.getTotalAmount());
        refund.setType(PaymentType.REFUND);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(PaymentMethod.BANK_TRANSFER);
        refund.setStatus(PaymentStatus.PAID);
        refund.setGatewayRef("MOCK-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        refund.setPaidAt(Instant.now());
        refund.setReceiptUrl("/receipts/refund-" + booking.getBookingCode() + ".pdf");

        return paymentTransactionRepository.save(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public long countBookingsByStatus(BookingStatus status) {
        return bookingRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalBookings() {
        return bookingRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Booking> findByBookingCode(String bookingCode) {
        return bookingRepository.findByBookingCode(bookingCode);
    }

    @Override
    @Transactional
    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalRevenue() {
        return paymentTransactionRepository.sumTotalPaidIn();
    }
}
