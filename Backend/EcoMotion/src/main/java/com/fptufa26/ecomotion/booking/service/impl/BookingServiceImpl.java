package com.fptufa26.ecomotion.booking.service.impl;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
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
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.CancelReason;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import com.fptufa26.ecomotion.booking.entity.PaymentDirection;
import com.fptufa26.ecomotion.booking.entity.PaymentStatus;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.entity.PaymentType;
import com.fptufa26.ecomotion.booking.mapper.BookingMapper;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.booking.service.DepositRefundRules;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.dispute.entity.DisputeCategory;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<BookingStatus> BLOCKING = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.CONFIRMED,
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

    private static final Set<BookingStatus> CANCELLABLE = EnumSet.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.CONFIRMED);
    private static final Set<BookingStatus> CHARGE_DISPUTE = EnumSet.of(
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
            BookingStatus.CANCELLED);

    @Override
    @Transactional
    public CreateBookingResponse create(Long userId, CreateBookingRequest request) {
        Renter renter = userService.requireRenterByUserId(userId);
        Vehicle vehicle = vehicleCatalogService.requireApproved(request.vehicleId());
        RentalPlan plan = vehicleCatalogService.requireActivePlan(request.vehicleId(), request.planId());

        int passengerLimit = plan.getPlanType() == PlanType.YEARLY
                ? vehicle.getSeatCount()
                : vehicle.getSeatCount() - 1;
        if (passengerLimit < 1 || request.passengerCount() > passengerLimit) {
            throw new ConflictException("Passenger count exceeds seat count");
        }

        Instant start = request.pickupTime();
        Instant end = start.plus(Duration.ofMinutes((long) plan.getSlotLengthMinutes() * request.slotCount()));
        if (!end.isAfter(start)) {
            throw new ConflictException("Rental range is empty");
        }
        if (!scheduleService.isRangeFree(vehicle.getId(), start, end)) {
            throw new ConflictException("Vehicle is not free in the selected range");
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
        Booking booking = requireAccepted(userId, bookingCode);
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        Instant end = endOf(booking, snapshot, slots);
        BigDecimal due = amountDue(booking, snapshot);
        String imageUrl = vehicleCatalogService.coverImageUrl(booking.getVehicle().getId());
        return bookingMapper.toCheckoutPreview(booking, snapshot, imageUrl, end, slots, due);
    }

    @Override
    @Transactional
    public CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request) {
        Booking booking = requireAccepted(userId, bookingCode);
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        Instant end = endOf(booking, snapshot, slots);
        if (!scheduleService.isRangeFree(booking.getVehicle().getId(), booking.getPickupTime(), end)) {
            throw new ConflictException("Vehicle is not free in the selected range");
        }

        BigDecimal due = amountDue(booking, snapshot);
        if (due == null || due.signum() <= 0) {
            throw new ConflictException("Amount due must be positive");
        }

        User actor = booking.getRenter().getUser();
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setAmount(due);
        payment.setDirection(PaymentDirection.IN);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());

        if (snapshot.getPlanType() == PlanType.YEARLY) {
            payment.setType(PaymentType.UPFRONT_PAY);
        } else {
            BookingCharge deposit = new BookingCharge();
            deposit.setBooking(booking);
            deposit.setChargeType(ChargeType.DEPOSIT);
            deposit.setDescription("Cọc giữ chỗ");
            deposit.setQuantity(null);
            deposit.setUnitPrice(booking.getTotalAmount());
            deposit.setAppliedRate(snapshot.getDepositRatio());
            deposit.setAmount(due);
            deposit.setCreatedBy(actor);
            deposit.setWaived(false);
            chargeRepository.save(deposit);
            payment.setType(PaymentType.DEPOSIT_PAY);
            payment.setCharge(deposit);
        }
        paymentTransactionRepository.save(payment);

        scheduleService.book(booking.getVehicle(), booking, booking.getPickupTime(), end);
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingMapper.toCheckoutResult(booking, payment, end);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse detail(Long userId, String bookingCode) {
        Booking booking = requireOwned(userId, bookingCode);
        return toDetail(booking);
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
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new ConflictException("Booking is not in progress");
        }
        if (booking.getRenterConfirmedAt() != null) {
            throw new ConflictException("Renter already confirmed the end");
        }
        booking.setRenterConfirmedAt(Instant.now());
        if (booking.getProviderConfirmedAt() != null) {
            booking.setStatus(BookingStatus.COMPLETED);
        }
        return bookingMapper.toConfirmEnd(booking);
    }

    @Override
    @Transactional
    public DisputeOpenedResponse openDispute(Long userId, String bookingCode, OpenDisputeRequest request) {
        Booking booking = requireOwned(userId, bookingCode);
        BookingCharge charge = null;
        if (request.category() == DisputeCategory.CHARGE) {
            if (request.chargeType() == null) {
                throw new BadRequestException("chargeType is required");
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
        return disputeService.open(booking, booking.getRenter().getUser(), request.category(), charge);
    }

    private BookingDetailResponse toDetail(Booking booking) {
        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        int slots = baseSlots(booking);
        List<BookingCharge> chargeRows = chargeRepository.findByBooking_IdOrderByIdAsc(booking.getId());
        List<BookingChargeLineResponse> charges = chargeRows.stream().map(bookingMapper::toChargeLine).toList();
        List<PaymentTransaction> payments = paymentTransactionRepository.findByBooking_Id(booking.getId());
        RefundPreviewResponse refundPreview = refundPreview(booking, snapshot, chargeRows, payments, Instant.now());
        boolean canConfirm = booking.getStatus() == BookingStatus.IN_PROGRESS && booking.getRenterConfirmedAt() == null;
        return bookingMapper.toDetail(
                booking,
                snapshot.getPlanType(),
                vehicleCatalogService.coverImageUrl(booking.getVehicle().getId()),
                endOf(booking, snapshot, slots),
                slots,
                charges,
                timeline(booking, payments),
                refundPreview,
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
            steps.add(new TimelineStepResponse("Chủ xe từ chối", null, "Không có lý do", true, true));
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
        return bookingMapper.toSummary(booking, planType, imageUrl);
    }

    private Booking requireOwned(Long userId, String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        Long ownerId = booking.getRenter().getUser().getUserId();
        if (!ownerId.equals(userId)) {
            throw new NotFoundException("Booking not found");
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
                .orElseThrow(() -> new NotFoundException("Booking snapshot not found"));
    }

    private int baseSlots(Booking booking) {
        return chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElseThrow(() -> new NotFoundException("Base rental charge not found"));
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
        List<Booking> existing = bookingRepository.findByRenter_IdAndStatusIn(renterId, BLOCKING);
        for (Booking booking : existing) {
            Instant otherEnd = endOf(booking);
            if (start.isBefore(otherEnd) && booking.getPickupTime().isBefore(end)) {
                return true;
            }
        }
        return false;
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
}
