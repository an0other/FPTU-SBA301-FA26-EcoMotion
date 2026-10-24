package com.fptufa26.ecomotion.dispute.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingService;
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

@Service
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final UserService userService;
    private final BookingService bookingService;
    private final PaymentRequestService paymentRequestService;

    public DisputeServiceImpl(
            DisputeRepository disputeRepository,
            UserService userService,
            @org.springframework.context.annotation.Lazy BookingService bookingService,
            PaymentRequestService paymentRequestService) {
        this.disputeRepository = disputeRepository;
        this.userService = userService;
        this.bookingService = bookingService;
        this.paymentRequestService = paymentRequestService;
    }

    @Override
    @Transactional
    public DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge) {
        if (category == DisputeCategory.CHARGE) {
            if (disputeRepository.existsByBooking_IdAndCategoryAndStatusAndCharge_Id(
                    booking.getId(), category, DisputeStatus.OPEN, charge.getId())) {
                throw new ConflictException("An open dispute already exists for this charge");
            }
        } else if (disputeRepository.existsByBooking_IdAndCategoryAndStatusAndChargeIsNull(
                booking.getId(), category, DisputeStatus.OPEN)) {
            throw new ConflictException("An open dispute already exists for this category");
        }

        Dispute dispute = new Dispute();
        dispute.setBooking(booking);
        dispute.setCharge(category == DisputeCategory.CHARGE ? charge : null);
        dispute.setRaisedBy(raisedBy);
        dispute.setHandledBy(null);
        dispute.setCategory(category);
        dispute.setStatus(DisputeStatus.OPEN);
        disputeRepository.save(dispute);
        return new DisputeOpenedResponse(dispute.getCategory(), dispute.getStatus());
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
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tranh chấp không tồn tại: " + disputeId));

        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.REJECTED) {
            throw new ConflictException("Tranh chấp đã được xử lý");
        }
        DisputeStatus targetStatus = status != null ? status : DisputeStatus.RESOLVED;
        if (dispute.getCategory() == DisputeCategory.CHARGE
                && (targetStatus == DisputeStatus.RESOLVED || targetStatus == DisputeStatus.REJECTED)) {
            resolveChargeDispute(dispute, decision);
        }

        if (adminUserId != null) {
            User admin = userService.findById(adminUserId).orElse(null);
            dispute.setHandledBy(admin);
        }

        dispute.setStatus(targetStatus);
        dispute.setResolution(resolution);

        return disputeRepository.save(dispute);
    }

    private void resolveChargeDispute(Dispute dispute, DisputeResolutionDecision decision) {
        if (decision == null) {
            throw new ConflictException("Cần chọn WAIVE_CHARGE hoặc KEEP_CHARGE");
        }
        BookingCharge charge = Optional.ofNullable(dispute.getCharge())
                .orElseThrow(() -> new ConflictException("Tranh chấp không liên kết với phụ phí"));
        Booking booking = dispute.getBooking();
        Optional<PaymentRequest> finalRequest = paymentRequestService
                .findByBookingIdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE);
        finalRequest.ifPresent(request -> {
            if (request.getStatus() == PaymentRequestStatus.CONFIRMED
                    || request.getPaidAmount().signum() > 0) {
                throw new ConflictException("Khoản quyết toán đã được ghi nhận, không thể xử lý tranh chấp");
            }
        });

        if (decision == DisputeResolutionDecision.WAIVE_CHARGE) {
            charge.setWaived(true);
        }

        BigDecimal remaining = paymentRequestService.calculateRemainingAmount(booking.getId());
        finalRequest.ifPresent(request -> {
            request.setPaidAmount(BigDecimal.ZERO);
            request.setConfirmedAt(null);
            request.setConfirmedBy(null);
            request.setRejectionReason(null);
            request.setIssueNote(null);
            if (remaining.signum() <= 0) {
                request.setStatus(PaymentRequestStatus.VOIDED);
                booking.setStatus(BookingStatus.COMPLETED);
            } else {
                request.setAmount(remaining);
                request.setStatus(PaymentRequestStatus.PENDING);
                booking.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
            }
            paymentRequestService.savePaymentRequest(request);
        });
        bookingService.saveBooking(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenOrInReview() {
        return disputeRepository.countOpenOrInReview();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalDisputes() {
        return disputeRepository.count();
    }
}
