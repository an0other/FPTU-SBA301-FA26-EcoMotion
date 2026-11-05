package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RefundReceiptService {
    private final RefundRequestRepository refunds;
    private final BookingPaymentLedgerService ledger;
    private final BookingNotificationService notifications;
    private final EntityManager entityManager;
    private final Clock clock;

    private RefundRequest locked(Long id) {
        var candidate = refunds.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy khoản hoàn"));
        ledger.requireBookingForUpdate(candidate.getBooking().getId());
        var refund = refunds.findByIdForUpdate(id).orElseThrow();
        entityManager.refresh(refund);
        return refund;
    }

    @Transactional
    public void respond(Long userId, Long id, boolean received) {
        var refund = locked(id);
        if (!refund.getBooking().getRenter().getUser().getId().equals(userId))
            throw new AccessDeniedException("Chỉ người thuê của đơn được xác nhận nhận tiền");
        if (refund.getStatus() != RefundStatus.REFUNDED || refund.getReceiptStatus() == null)
            throw new ConflictException("Khoản hoàn chưa có xác nhận chuyển tiền");
        if ("AUTO_CONFIRMED".equals(refund.getReceiptStatus()))
            throw new ConflictException("Khoản hoàn đã được hệ thống xác nhận khi hết thời hạn");
        if ("WAITING".equals(refund.getReceiptStatus()) && refund.getReceiptDeadline() != null
                && !refund.getReceiptDeadline().isAfter(clock.instant()))
            throw new ConflictException("Đã hết thời hạn phản hồi. Vui lòng tải lại trạng thái khoản hoàn");
        if (received && "CONFIRMED".equals(refund.getReceiptStatus())) return;
        if (!received && "REPORTED_NOT_RECEIVED".equals(refund.getReceiptStatus())) return;
        if (received) {
            refund.setReceiptStatus("CONFIRMED");
            refund.setReceiptConfirmedAt(clock.instant());
            refund.setReceiptConfirmedBy(userId);
        } else {
            if ("CONFIRMED".equals(refund.getReceiptStatus()))
                throw new ConflictException("Bạn đã xác nhận nhận khoản tiền này");
            refund.setReceiptStatus("REPORTED_NOT_RECEIVED");
            refund.setReceiptReportedAt(clock.instant());
            refund.setReceiptConfirmedAt(null);
            refund.setReceiptConfirmedBy(null);
        }
        notifications.changed(refund.getBooking(), "REFUND_RECEIPT_UPDATED");
    }

    @Transactional
    public void confirmExpired() {
        for (var candidate : refunds.findByReceiptStatusAndReceiptDeadlineLessThanEqual("WAITING", clock.instant())) {
            var refund = locked(candidate.getId());
            if (refund.getStatus() == RefundStatus.REFUNDED && "WAITING".equals(refund.getReceiptStatus())
                    && refund.getReceiptDeadline() != null && !refund.getReceiptDeadline().isAfter(clock.instant())) {
                refund.setReceiptStatus("AUTO_CONFIRMED");
                refund.setReceiptAutoConfirmedAt(clock.instant());
                refund.setReceiptConfirmedAt(clock.instant());
                notifications.changed(refund.getBooking(), "REFUND_RECEIPT_AUTO_CONFIRMED");
            }
        }
    }
}
