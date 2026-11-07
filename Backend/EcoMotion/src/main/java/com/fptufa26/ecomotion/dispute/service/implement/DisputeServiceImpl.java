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
                && u.getStatus() == com.fptufa26.ecomotion.user.enums.UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Chỉ admin được phân xử"));
        if (!java.util.Set.of(DisputeCategory.YEARLY_SETTLEMENT, DisputeCategory.YEARLY_CANCELLATION).contains(dispute.getCategory())
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
    private final com.fptufa26.ecomotion.booking.service.implement.TripEndConfirmationService endConfirmations;
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
        if (java.util.Set.of(DisputeCategory.YEARLY_SETTLEMENT, DisputeCategory.YEARLY_CANCELLATION).contains(dispute.getCategory())
                && status != DisputeStatus.IN_REVIEW)
            throw new ConflictException("Hồ sơ năm phải dùng phân xử tài chính năm để áp dụng kết quả");
        if(tripEndAction!=null && (dispute.getCategory()!=DisputeCategory.TRIP_NOT_CONFIRMED || status==DisputeStatus.IN_REVIEW))
            throw new ConflictException("Hành động kết thúc chỉ dùng khi phân xử khiếu nại kết thúc");
        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.REJECTED) {
            boolean sameAmount = dispute.getRefundAwardTotal() == null ? refundAwardTotal == null
                    : refundAwardTotal != null && dispute.getRefundAwardTotal().compareTo(refundAwardTotal) == 0;
            if (dispute.getStatus() == status && dispute.getDecision() == decision && Objects.equals(dispute.getResolution(), note) && sameAmount) {
                if(tripEndAction==null || tripEndAction.equals(dispute.getTripEndAction())) return dispute;
                if(dispute.getTripEndAction()!=null) throw new ConflictException("Hành động kết thúc đã được thực hiện");
                endConfirmations.adminAction(booking,disputeId,adminUserId,tripEndAction,note);
                dispute.setTripEndAction(tripEndAction);
                return dispute;
            }
            throw new ConflictException("Khiếu nại đã phân xử; không thể thay đổi phán quyết");
        }
        boolean charge = dispute.getCategory() == DisputeCategory.CHARGE;
        boolean deposit = dispute.getCategory() == DisputeCategory.DEPOSIT_REFUND;
        if (status == DisputeStatus.IN_REVIEW && (decision != null || refundAwardTotal != null)
                || status != DisputeStatus.IN_REVIEW && charge && decision != (status == DisputeStatus.RESOLVED
                    ? DisputeResolutionDecision.WAIVE_CHARGE : DisputeResolutionDecision.KEEP_CHARGE)
                || !charge && decision != null
                || !(deposit && status == DisputeStatus.RESOLVED) && refundAwardTotal != null)
            throw new ConflictException("Phán quyết, trạng thái và số tiền không phù hợp với loại khiếu nại");
        dispute.setStatus(status);
        dispute.setResolution(note);
        dispute.setHandledBy(admin);
        dispute.setDecision(decision);
        dispute.setRefundAwardTotal(refundAwardTotal);
        if (status != DisputeStatus.IN_REVIEW) dispute.setResolvedAt(clock.instant());
        disputeRepository.saveAndFlush(dispute);
        if (charge && status != DisputeStatus.IN_REVIEW) resolveChargeDispute(dispute, decision);
        if (deposit && status == DisputeStatus.RESOLVED) {
            if (booking.getStatus() != BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.CANCELLATION_REVIEW)
                throw new ConflictException("Chỉ phân xử hoàn cọc của đơn đã hủy");
            dispute.getRelatedRefunds().addAll(refundEventService.awardDepositRefund(booking.getId(), dispute.getId(), refundAwardTotal, admin));
            if (refundEventService.hasOpenRefunds(booking.getId())) booking.setStatus(BookingStatus.CANCELLATION_REVIEW);
            bookingPaymentLedgerService.saveBooking(booking);
        }
        if(tripEndAction!=null) {
            endConfirmations.adminAction(booking,disputeId,adminUserId,tripEndAction,note);
            dispute.setTripEndAction(tripEndAction);
        }
        if (status != DisputeStatus.IN_REVIEW) refreshFinalPayment(booking);
        notifications.changed(booking, "DISPUTE_UPDATED");
        return dispute;
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
            charge.setConfirmationStatus(com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.ACCEPTED);
            bookingPaymentLedgerService.saveCharge(charge);
            BigDecimal obligation = booking.getTotalAmount() == null ? BigDecimal.ZERO : booking.getTotalAmount();
            obligation = obligation.add(bookingPaymentLedgerService.listCharges(booking.getId()).stream()
                    .filter(item -> !item.isWaived()
                            && item.getChargeType() != com.fptufa26.ecomotion.booking.enums.ChargeType.BASE_RENTAL
                            && item.getChargeType() != com.fptufa26.ecomotion.booking.enums.ChargeType.DEPOSIT
                            && item.getChargeType() != com.fptufa26.ecomotion.booking.enums.ChargeType.WEEKEND)
                    .map(com.fptufa26.ecomotion.booking.entity.BookingCharge::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal open = refundEventService.openAllocated(booking.getId(), obligation);
            if (open.signum() > 0) {
                User actor = dispute.getRaisedBy();
                dispute.getRelatedRefunds().addAll(refundEventService.reserve(booking.getId(), "WAIVE:" + charge.getId(), open,
                        "Hoàn phần đã thu sau khi miễn phụ phí", actor));
            }
        } else {
            charge.setConfirmationStatus(com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus.ACCEPTED);
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
                    booking.setStatus(BookingStatus.COMPLETED);
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
