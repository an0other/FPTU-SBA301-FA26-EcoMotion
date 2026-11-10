package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.finance.dto.request.RefundReconcileRequest;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.common.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefundReconciliationServiceImpl implements RefundReconciliationService {
    private final RefundRequestRepository refunds;
    private final BookingPaymentLedgerService ledger;
    private final UserService users;
    private final FinancialAuditService audit;
    private final CancellationClosureService closure;
    private final BookingNotificationService notifications;
    private final EntityManager em;
    private final Clock clock;

    private void admin(Long id) {
        if (users.findById(id).filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE).isEmpty())
            throw new AccessDeniedException("Chỉ quản trị viên đang hoạt động được phân xử hoàn tiền");
    }

    @Override
    @Transactional
    public void reconcile(Long adminId, Long refundId, RefundReconcileRequest r) {
        admin(adminId);
        if (r == null || r.note() == null || r.note().isBlank() || r.evidenceReference() == null || r.evidenceReference().isBlank()
                || r.transactionReference() == null || r.transactionReference().isBlank() || r.requestId() == null || r.requestId().isBlank())
            throw new ConflictException("Cần mã yêu cầu, kết luận và chứng cứ đối soát");
        Long bookingId = refunds.findBookingIdById(refundId).orElseThrow(() -> new NotFoundException("Không có khoản hoàn"));
        ledger.requireBookingForUpdate(bookingId);
        var refund = refunds.findByIdForUpdate(refundId).orElseThrow(); em.refresh(refund);
        if (audit.replay("REFUND", refundId, r.requestId(), adminId, r.toString())) return;
        if (!java.util.Objects.equals(refund.getVersion(), r.version())) throw new ConflictException("Dữ liệu hoàn tiền đã thay đổi");
        if (!Set.of("HOLD", "TRANSFER_VERIFIED", "FAILED_VERIFIED", "WRONG_RECIPIENT").contains(r.outcome()))
            throw new ConflictException("Kết quả đối soát không hợp lệ");
        if (!Set.of(RefundStatus.PENDING_RECONCILIATION, RefundStatus.PROCESSING, RefundStatus.UNDER_REVIEW, RefundStatus.FAILED).contains(refund.getStatus())
                && !"REPORTED_NOT_RECEIVED".equals(refund.getReceiptStatus())) throw new ConflictException("Khoản hoàn không cần đối soát");
        var out = refund.getPaymentTransaction();
        if (out == null) throw new ConflictException("Thiếu giao dịch hoàn gốc");
        String previous = refund.getStatus() + ":" + refund.getReceiptStatus() + ":" + out.getStatus() + ":" + out.getGatewayRef()
                + ":" + out.getReceiptUrl() + ":" + refund.getVnpRequestId() + ":" + refund.getAmount();
        var actor = users.findById(adminId).orElseThrow();
        String reference = r.transactionReference().trim().toUpperCase(java.util.Locale.ROOT);
        if ("TRANSFER_VERIFIED".equals(r.outcome())) {
            if (!java.util.Objects.equals(out.getGatewayRef(), reference) && ledger.gatewayReferenceExists(reference))
                throw new ConflictException("Mã giao dịch đã dùng cho khoản khác");
            if (out.getGatewayRef() != null && !out.getGatewayRef().equals(reference)
                    && refund.getStatus() == RefundStatus.REFUNDED) throw new ConflictException("Mã chuyển tiền không khớp khoản đã ghi nhận");
            out.setStatus(PaymentStatus.PAID); out.setPaidAt(clock.instant()); out.setGatewayRef(reference);
            out.setConfirmedBy(actor); out.setVerificationMethod(PaymentVerificationMethod.ADMIN_RECONCILED);
            refund.setStatus(RefundStatus.REFUNDED); refund.setGatewayRefundRef(reference);
            refund.setReceiptStatus("ADMIN_VERIFIED"); refund.setReceiptConfirmedAt(clock.instant()); refund.setReceiptConfirmedBy(adminId);
        } else if ("FAILED_VERIFIED".equals(r.outcome())) {
            out.setStatus(PaymentStatus.FAILED);
            refund.setStatus(RefundStatus.UNDER_REVIEW);
            refund.setReceiptStatus(null); refund.setReceiptDeadline(null); refund.setReceiptConfirmedAt(null); refund.setReceiptConfirmedBy(null);
            refund.setVnpResponseCode("ADMIN_FAIL");
            refund.setVnpRequestId(null); refund.setGatewaySubmittedAt(null);
        } else if ("WRONG_RECIPIENT".equals(r.outcome())) {
            refund.setStatus(RefundStatus.PENDING_RECONCILIATION);
        }
        out.setUpdatedAt(clock.instant());
        refund.setApprovedBy(actor); refund.setUpdatedAt(clock.instant());
        audit.record("REFUND", refundId, r.requestId(), adminId, r.outcome(), r.toString(), previous);
        refunds.flush(); closure.reconcile(refund.getBooking().getId());
        notifications.changed(refund.getBooking(), "REFUND_UPDATED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancialAuditService.Entry> history(Long adminId, Long refundId) {
        admin(adminId);
        return audit.history("REFUND", refundId);
    }
}
