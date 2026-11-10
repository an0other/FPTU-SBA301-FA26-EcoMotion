package com.fptufa26.ecomotion.dispute.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.time.Clock;
import jakarta.persistence.EntityManager;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.user.enums.Role;
import org.springframework.security.access.AccessDeniedException;
import com.fptufa26.ecomotion.finance.service.RefundEventService;
import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.service.implement.TripEndConfirmationService;
import com.fptufa26.ecomotion.user.enums.UserStatus;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    @Override
    @Transactional
    public void resolveYearlyReview(Long disputeId, Long adminId, String resolution) {
        var candidate = disputeRepository.findById(disputeId).orElseThrow(() -> new ResourceNotFoundException("Không có khiếu nại"));
        var booking = bookingPaymentLedgerService.requireBookingForUpdate(candidate.getBooking().getId());
        var dispute = disputeRepository.findByIdForUpdate(disputeId).orElseThrow();
        var admin = userService.findById(adminId).filter(u -> u.getRole() == Role.ADMIN
                && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Chỉ admin được phân xử"));
        if (!java.util.Set.of(DisputeCategory.YEARLY_SETTLEMENT, DisputeCategory.YEARLY_CANCELLATION, DisputeCategory.YEARLY_REFUND, DisputeCategory.YEARLY_INCIDENT).contains(dispute.getCategory())
                || !java.util.Set.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW).contains(dispute.getStatus())
                || resolution == null || resolution.isBlank()) throw new ConflictException("Hồ sơ năm không còn chờ phân xử");
        dispute.setStatus(DisputeStatus.RESOLVED); dispute.setResolution(resolution.trim());
        dispute.setHandledBy(admin); dispute.setResolvedAt(clock.instant());
        disputeRepository.saveAndFlush(dispute);
        notifications.changed(booking, "DISPUTE_UPDATED");
    }

    private final DisputeRepository disputeRepository;
    private final UserService userService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TripEndConfirmationService endConfirmations;
    private final BookingNotificationService notifications;
    private final BookingPaymentLedgerService bookingPaymentLedgerService;
    private final PaymentRequestService paymentRequestService;
    private final RefundEventService refundEventService;

    @Override
    @Transactional
    public DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge) {
        return open(booking, raisedBy, category, charge, null, null);
    }

    @Override
    @Transactional
    public DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge,
                                      String description, List<String> evidenceUrls) {
        bookingPaymentLedgerService.requireBookingForUpdate(booking.getId());
        boolean duplicate = disputeRepository.findByBooking_Id(booking.getId()).stream()
                .filter(d -> d.getStatus() == DisputeStatus.OPEN || d.getStatus() == DisputeStatus.IN_REVIEW)
                .anyMatch(d -> d.getCategory() == category && (category != DisputeCategory.CHARGE
                        || d.getCharge() != null && charge != null && d.getCharge().getId().equals(charge.getId())));
        if (duplicate) throw new ConflictException("Khiếu nại này đang được xử lý. Vui lòng theo dõi kết quả hiện có");

        if(category==DisputeCategory.TRIP_NOT_CONFIRMED) endConfirmations.ensureCanObject(booking);
        Dispute dispute = new Dispute();
        dispute.setBooking(booking);
        dispute.setCharge(category == DisputeCategory.CHARGE ? charge : null);
        dispute.setRaisedBy(raisedBy);
        dispute.setHandledBy(null);
        dispute.setCategory(category);
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setDescription(description);
        dispute.setEvidenceUrlList(evidenceUrls);
        disputeRepository.save(dispute);
        if(category == DisputeCategory.TRIP_NOT_CONFIRMED) endConfirmations.pause(booking, dispute.getId());
        notifications.changed(booking, "DISPUTE_OPENED");
        return new DisputeOpenedResponse(
                dispute.getCategory(),
                dispute.getStatus(),
                dispute.getId(),
                dispute.getDescription(),
                dispute.getEvidenceUrlList()
        );
    }

    @Override
    @Transactional
    public void attachPaymentMismatch(Long disputeId, Long paymentRequestId, BigDecimal claimedAmount, String mismatchType) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tranh chấp không tồn tại: " + disputeId));
        dispute.setPaymentRequestId(paymentRequestId);
        dispute.setClaimedAmount(claimedAmount);
        dispute.setMismatchType(mismatchType);
        disputeRepository.save(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal recordedForPaymentRequest(Long bookingId, Long paymentRequestId) {
        return bookingPaymentLedgerService.listTransactions(bookingId).stream()
                .filter(t -> t.getDirection() == PaymentDirection.IN
                        && t.getStatus() == PaymentStatus.PAID
                        && t.getPaymentRequest() != null && t.getPaymentRequest().getId().equals(paymentRequestId))
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> listDisputes(DisputeStatus status) {
        if (status != null) {
            return disputeRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return disputeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dispute> getDisputeById(Long disputeId) {
        return disputeRepository.findById(disputeId);
    }

    @Override
    @Transactional
    public Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                                  DisputeResolutionDecision decision, String resolution) {
        return resolveDispute(disputeId, adminUserId, status, decision, resolution, null);
    }

    @Override
    @Transactional
    public Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                                  DisputeResolutionDecision decision, String resolution, BigDecimal refundAwardTotal) {
        return resolveDispute(disputeId,adminUserId,status,decision,resolution,refundAwardTotal,null);
    }

    @Override
    @Transactional
    public Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                                  DisputeResolutionDecision decision, String resolution, BigDecimal refundAwardTotal, String tripEndAction) {
        return resolveDispute(disputeId, adminUserId, status, decision, resolution, refundAwardTotal, tripEndAction, null);
    }

    private static final java.util.Set<String> VERDICT_ACTIONS = java.util.Set.of("ACCEPT_RENTER_TIME", "KEEP_DRIVER_TIME");

    @Override
    @Transactional
    public Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status, DisputeResolutionDecision decision,
                                  String resolution, BigDecimal refundAwardTotal, String tripEndAction, java.time.Instant correctedEndAt) {
        Dispute candidate = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tranh chấp không tồn tại: " + disputeId));
        Booking booking = bookingPaymentLedgerService.requireBookingForUpdate(candidate.getBooking().getId());
        Dispute dispute = disputeRepository.findByIdForUpdate(disputeId).orElseThrow();
        entityManager.refresh(dispute);
        User admin = userService.findById(adminUserId).filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được phân xử"));
        if (status == null || status == DisputeStatus.OPEN || resolution == null || resolution.isBlank() || resolution.trim().length() > 1000)
            throw new ConflictException("Cần trạng thái thụ lý/phán quyết hợp lệ và kết luận từ 1 đến 1000 ký tự");
        String note = resolution.trim();
        if (java.util.Set.of(DisputeCategory.YEARLY_SETTLEMENT, DisputeCategory.YEARLY_CANCELLATION, DisputeCategory.YEARLY_REFUND, DisputeCategory.YEARLY_INCIDENT).contains(dispute.getCategory())
                && status != DisputeStatus.IN_REVIEW)
            throw new ConflictException("Hồ sơ năm phải dùng phân xử tài chính năm để áp dụng kết quả");
        if(tripEndAction!=null && (dispute.getCategory()!=DisputeCategory.TRIP_NOT_CONFIRMED || status==DisputeStatus.IN_REVIEW))
            throw new ConflictException("Hành động kết thúc chỉ dùng khi phân xử khiếu nại kết thúc");
        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.REJECTED) {
            boolean sameAmount = dispute.getRefundAwardTotal() == null ? refundAwardTotal == null
                    : refundAwardTotal != null && dispute.getRefundAwardTotal().compareTo(refundAwardTotal) == 0;
            if (dispute.getStatus() == status && dispute.getDecision() == decision && Objects.equals(dispute.getResolution(), note) && sameAmount) {
                if(tripEndAction==null || tripEndAction.equals(dispute.getTripEndAction())) return dispute;
                if(dispute.getTripEndAction()!=null || VERDICT_ACTIONS.contains(tripEndAction))
                    throw new ConflictException("Hành động kết thúc đã được thực hiện");
                endConfirmations.adminAction(booking,disputeId,adminUserId,tripEndAction,note);
                dispute.setTripEndAction(tripEndAction);
                return dispute;
            }
            throw new ConflictException("Khiếu nại đã phân xử; không thể thay đổi phán quyết");
        }
        boolean charge = dispute.getCategory() == DisputeCategory.CHARGE;
        boolean deposit = dispute.getCategory() == DisputeCategory.DEPOSIT_REFUND;
        boolean mismatch = dispute.getCategory() == DisputeCategory.PAYMENT_MISMATCH;
        boolean partial = charge && decision == DisputeResolutionDecision.PARTIAL_WAIVE;
        boolean chargeDecisionValid = status == DisputeStatus.RESOLVED
                ? decision == DisputeResolutionDecision.WAIVE_CHARGE || partial
                : decision == DisputeResolutionDecision.KEEP_CHARGE;
        if (status == DisputeStatus.IN_REVIEW && (decision != null || refundAwardTotal != null)
                || status != DisputeStatus.IN_REVIEW && charge && !chargeDecisionValid
                || !charge && decision != null
                || !((deposit || mismatch || partial) && status == DisputeStatus.RESOLVED) && refundAwardTotal != null
                || partial && refundAwardTotal == null)
            throw new ConflictException("Phán quyết, trạng thái và số tiền không phù hợp với loại khiếu nại");
        dispute.setStatus(status);
        dispute.setResolution(note);
        dispute.setHandledBy(admin);
        dispute.setDecision(decision);
        dispute.setRefundAwardTotal(refundAwardTotal);
        if (status != DisputeStatus.IN_REVIEW) dispute.setResolvedAt(clock.instant());
        disputeRepository.saveAndFlush(dispute);
        if (partial) partiallyWaiveCharge(dispute, refundAwardTotal);
        else if (charge && status != DisputeStatus.IN_REVIEW) resolveChargeDispute(dispute, decision);
        if (deposit && status == DisputeStatus.RESOLVED) {
            if (booking.getStatus() != BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.CANCELLATION_REVIEW)
                throw new ConflictException("Chỉ phân xử hoàn cọc của đơn đã hủy");
            dispute.getRelatedRefunds().addAll(refundEventService.awardDepositRefund(booking.getId(), dispute.getId(), refundAwardTotal, admin));
            if (refundEventService.hasOpenRefunds(booking.getId())) booking.setStatus(BookingStatus.CANCELLATION_REVIEW);
            bookingPaymentLedgerService.saveBooking(booking);
        }
        if (mismatch && status == DisputeStatus.RESOLVED && refundAwardTotal != null) {
            // refundAwardTotal = tổng số khách thực chuyển cho yêu cầu này (admin đã đối chiếu sao kê).
            if (dispute.getPaymentRequestId() == null)
                throw new ConflictException("Khiếu nại không gắn với yêu cầu thanh toán");
            dispute.getRelatedRefunds().addAll(paymentRequestService.applyMismatchResolution(
                    dispute.getPaymentRequestId(), refundAwardTotal, admin, dispute.getId()));
        }
        if ("ACCEPT_RENTER_TIME".equals(tripEndAction)) {
            if (status != DisputeStatus.RESOLVED) throw new ConflictException("Chốt theo giờ khách là chấp thuận khiếu nại (RESOLVED)");
            acceptRenterEndTime(booking, dispute, correctedEndAt, admin);
            endConfirmations.adminSettled(booking, disputeId, adminUserId,
                    note + " · Chốt theo giờ khách: " + correctedEndAt);
            dispute.setTripEndAction(tripEndAction);
        } else if ("KEEP_DRIVER_TIME".equals(tripEndAction)) {
            endConfirmations.adminSettled(booking, disputeId, adminUserId, note + " · Giữ giờ tài xế báo");
            dispute.setTripEndAction(tripEndAction);
        } else if(tripEndAction!=null) {
            endConfirmations.adminAction(booking,disputeId,adminUserId,tripEndAction,note);
            dispute.setTripEndAction(tripEndAction);
        } else if (dispute.getCategory() == DisputeCategory.TRIP_NOT_CONFIRMED && status != DisputeStatus.IN_REVIEW) {
            endConfirmations.adminSettled(booking, disputeId, adminUserId, note);
        }
        if (status != DisputeStatus.IN_REVIEW) refreshFinalPayment(booking);
        notifications.changed(booking, "DISPUTE_UPDATED");
        return dispute;
    }

    /**
     * Phán quyết "theo giờ khách": chốt giờ kết thúc admin xác minh và tính lại phí quá giờ. Chỉ giảm, không tăng
     * phụ phí (khách khiếu nại không thể bị tính thêm); phần đã thu dư được tạo khoản hoàn.
     */
    private void acceptRenterEndTime(Booking booking, Dispute dispute, java.time.Instant correctedEndAt, User admin) {
        if (correctedEndAt == null) throw new ConflictException("Cần nhập giờ kết thúc đúng theo xác minh");
        java.time.Instant reported = booking.getActualEndAt() != null ? booking.getActualEndAt() : booking.getDriverEndConfirmedAt();
        if (booking.getActualStartAt() != null && correctedEndAt.isBefore(booking.getActualStartAt()))
            throw new ConflictException("Giờ kết thúc không thể trước giờ bắt đầu chuyến");
        if (reported != null && correctedEndAt.isAfter(reported))
            throw new ConflictException("Giờ theo khách phải sớm hơn hoặc bằng giờ tài xế báo");
        booking.setActualEndAt(correctedEndAt);
        bookingPaymentLedgerService.saveBooking(booking);

        var snapshot = bookingPaymentLedgerService.findSnapshot(booking.getId()).orElse(null);
        int slot = snapshot != null && snapshot.getSlotLengthMinutes() != null && snapshot.getSlotLengthMinutes() > 0
                ? snapshot.getSlotLengthMinutes() : 60;
        int grace = snapshot != null && snapshot.getAllowedOvertimeMinutes() != null ? snapshot.getAllowedOvertimeMinutes() : 0;
        long overMinutes = booking.getPlannedEndAt() == null ? 0
                : java.time.Duration.between(booking.getPlannedEndAt(), correctedEndAt).toMinutes() - grace;
        int allowedSlots = overMinutes <= 0 ? 0 : (int) ((overMinutes + slot - 1) / slot);

        int remaining = allowedSlots;
        boolean changed = false;
        for (BookingCharge overtime : bookingPaymentLedgerService.listCharges(booking.getId())) {
            if (overtime.isWaived() || overtime.getChargeType() != ChargeType.OVERTIME) continue;
            int quantity = overtime.getQuantity() == null ? 1 : overtime.getQuantity();
            if (remaining <= 0) {
                overtime.setWaived(true);
                overtime.setWaivedSource("ADMIN_DISPUTE");
                changed = true;
            } else if (quantity > remaining) {
                BigDecimal perSlot = overtime.getAmount().divide(BigDecimal.valueOf(quantity), 2, java.math.RoundingMode.HALF_UP);
                overtime.setQuantity(remaining);
                overtime.setAmount(perSlot.multiply(BigDecimal.valueOf(remaining)));
                overtime.setDescription(trim500(overtime.getDescription() + " (admin chốt lại theo giờ khách)"));
                remaining = 0;
                changed = true;
            } else {
                remaining -= quantity;
            }
            if (overtime.getConfirmationStatus() == ChargeConfirmationStatus.DISPUTED)
                overtime.setConfirmationStatus(ChargeConfirmationStatus.ACCEPTED);
            bookingPaymentLedgerService.saveCharge(overtime);
        }
        if (changed) reserveOverCollected(booking, "TRIP_END:" + dispute.getId(), "Hoàn phần phí quá giờ đã thu sau khi chốt lại giờ kết thúc", dispute, admin);
    }

    /** Admin giảm một phần phụ phí bị khiếu nại. */
    private void partiallyWaiveCharge(Dispute dispute, BigDecimal reduction) {
        BookingCharge charge = Optional.ofNullable(dispute.getCharge())
                .orElseThrow(() -> new ConflictException("Tranh chấp không liên kết với phụ phí"));
        if (reduction.signum() <= 0 || reduction.compareTo(charge.getAmount()) >= 0)
            throw new ConflictException("Số tiền giảm phải lớn hơn 0 và nhỏ hơn số tiền phụ phí; giảm toàn bộ thì chọn miễn phụ phí");
        charge.setAmount(charge.getAmount().subtract(reduction).setScale(2, java.math.RoundingMode.HALF_UP));
        charge.setDescription(trim500(charge.getDescription() + " (admin giảm " + reduction.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + " đ)"));
        charge.setConfirmationStatus(ChargeConfirmationStatus.ACCEPTED);
        bookingPaymentLedgerService.saveCharge(charge);
        reserveOverCollected(dispute.getBooking(), "PARTIAL:" + charge.getId(), "Hoàn phần phụ phí đã thu sau khi admin giảm", dispute, dispute.getRaisedBy());
    }

    /** Nếu khách đã trả nhiều hơn nghĩa vụ mới, tạo khoản hoàn phần chênh. */
    private void reserveOverCollected(Booking booking, String eventId, String reason, Dispute dispute, User actor) {
        BigDecimal obligation = booking.getTotalAmount() == null ? BigDecimal.ZERO : booking.getTotalAmount();
        obligation = obligation.add(bookingPaymentLedgerService.listCharges(booking.getId()).stream()
                .filter(item -> !item.isWaived()
                        && item.getChargeType() != ChargeType.BASE_RENTAL
                        && item.getChargeType() != ChargeType.DEPOSIT
                        && item.getChargeType() != ChargeType.WEEKEND)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal open = refundEventService.openAllocated(booking.getId(), obligation);
        if (open.signum() > 0) dispute.getRelatedRefunds().addAll(refundEventService.reserve(booking.getId(), eventId, open, reason, actor));
    }

    private static String trim500(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void resolveChargeDispute(Dispute dispute, DisputeResolutionDecision decision) {
        if (decision == null) {
            throw new ConflictException("Cần chọn WAIVE_CHARGE hoặc KEEP_CHARGE");
        }
        BookingCharge charge = Optional.ofNullable(dispute.getCharge())
                .orElseThrow(() -> new ConflictException("Tranh chấp không liên kết với phụ phí"));
        Booking booking = dispute.getBooking();
        if (decision == DisputeResolutionDecision.WAIVE_CHARGE) {
            charge.setWaived(true);
            charge.setWaivedSource("ADMIN_DISPUTE");
            charge.setConfirmationStatus(ChargeConfirmationStatus.ACCEPTED);
            bookingPaymentLedgerService.saveCharge(charge);
            // B1: trừ khoản giảm trừ do đổi sang xe rẻ hơn (Booking.vehicleChangeCredit) khỏi nghĩa vụ; xe đắt hơn thì bằng 0, không thu thêm.
            BigDecimal obligation = booking.getTotalAmount() == null ? BigDecimal.ZERO
                    : booking.getTotalAmount().subtract(booking.vehicleChangeCredit()).max(BigDecimal.ZERO);
            obligation = obligation.add(bookingPaymentLedgerService.listCharges(booking.getId()).stream()
                    .filter(item -> !item.isWaived()
                            && item.getChargeType() != ChargeType.BASE_RENTAL
                            && item.getChargeType() != ChargeType.DEPOSIT
                            && item.getChargeType() != ChargeType.WEEKEND)
                    .map(BookingCharge::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal open = refundEventService.openAllocated(booking.getId(), obligation);
            if (open.signum() > 0) {
                User actor = dispute.getRaisedBy();
                dispute.getRelatedRefunds().addAll(refundEventService.reserve(booking.getId(), "WAIVE:" + charge.getId(), open,
                        "Hoàn phần đã thu sau khi miễn phụ phí", actor));
            }
        } else {
            charge.setConfirmationStatus(ChargeConfirmationStatus.ACCEPTED);
            bookingPaymentLedgerService.saveCharge(charge);
        }

    }

    private void refreshFinalPayment(Booking booking) {
        if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT
                && booking.getStatus() != BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION
                && booking.getStatus() != BookingStatus.COMPLETED) return;
        Optional<PaymentRequest> finalRequest = paymentRequestService.findPaymentRequest(booking.getId(), PaymentRequestType.FINAL_BALANCE);
        BigDecimal remaining = paymentRequestService.calculateRemainingAmount(booking.getId());
        finalRequest.ifPresent(request -> {
            if (hasActiveDispute(booking.getId())) {
                request.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                request.setIssueNote("Còn khiếu nại chưa được giải quyết");
            } else {
                request.setRejectionReason(null);
                request.setIssueNote(null);
                BigDecimal paid = request.getPaidAmount() == null ? BigDecimal.ZERO : request.getPaidAmount();
                if (remaining.signum() <= 0) {
                    if (paid.signum() > 0) request.setAmount(paid);
                    request.setStatus(paid.signum() > 0 ? PaymentRequestStatus.CONFIRMED : PaymentRequestStatus.VOIDED);
                    if (endConfirmations.isEndSettled(booking.getId())
                            && !refundEventService.hasOpenExcessRefunds(booking.getId())) {
                        booking.setStatus(BookingStatus.COMPLETED);
                    } else {
                        booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
                        booking.setSettlementRequestedAt(clock.instant());
                    }
                } else {
                    request.setAmount(paid.add(remaining));
                    request.setExpiresAt(clock.instant().plus(java.time.Duration.ofHours(48)));
                    request.setStatus(paid.signum() > 0 ? PaymentRequestStatus.PARTIALLY_PAID : PaymentRequestStatus.PENDING);
                    booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
                }
            }
            paymentRequestService.savePaymentRequest(request);
        });
        bookingPaymentLedgerService.saveBooking(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenOrInReview() {
        return disputeRepository.countOpenOrInReview();
    }

    @Override
    public boolean hasOpenDispute(Long bookingId) {
        return disputeRepository.existsByBooking_IdAndStatusIn(
                bookingId, List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW));
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalDisputes() {
        return disputeRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveDispute(Long bookingId) {
        return disputeRepository.existsByBooking_IdAndStatusIn(
                bookingId, List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByBookingId(Long bookingId) {
        return disputeRepository.findByBooking_Id(bookingId);
    }
}
