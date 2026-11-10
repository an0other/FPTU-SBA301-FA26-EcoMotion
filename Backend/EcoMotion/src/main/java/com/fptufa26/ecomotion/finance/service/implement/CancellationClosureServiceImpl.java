package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.*;
import com.fptufa26.ecomotion.finance.service.CancellationClosureService;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;

@Service
@RequiredArgsConstructor
public class CancellationClosureServiceImpl implements CancellationClosureService {
    private final BookingPaymentLedgerService ledger;
    private final RefundRequestRepository refunds;
    private final PaymentRequestRepository requests;
    private final org.springframework.beans.factory.ObjectProvider<DisputeQueryService> disputeQueries;
    private final BookingNotificationService notifications;

    @Override
    @Transactional
    public boolean reconcile(Long bookingId) {
        var booking = ledger.requireBookingForUpdate(bookingId);
        if (booking.getStatus() != BookingStatus.CANCELLATION_REVIEW && booking.getStatus() != BookingStatus.CANCELLED) return false;
        var rows = refunds.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        var transactions = ledger.listTransactions(bookingId);
        for (var source : transactions) {
            if (source.getDirection() != PaymentDirection.IN || source.getStatus() != PaymentStatus.PAID
                    || !RefundMath.unapplied(source.getProcessingNote())) continue;
            var returned = rows.stream().filter(r -> r.getStatus() == RefundStatus.REFUNDED
                            && r.getSourcePaymentTransaction() != null && r.getSourcePaymentTransaction().getId().equals(source.getId()))
                    .map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (returned.compareTo(source.getAmount()) >= 0) {
                source.setProcessingNote("RECONCILED_REFUND: " + source.getProcessingNote());
                ledger.saveTransaction(source);
            }
        }
        // A statement-verified receipt exception is resolved only when every linked cash receipt was
        // explicitly returned in full. Do not clear other review types, pending gateway attempts or proofs.
        for (var request : requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)) {
            if (request.getStatus() != PaymentRequestStatus.REVIEW_REQUIRED) continue;
            var linked = transactions.stream().filter(t -> t.getPaymentRequest() != null
                    && t.getPaymentRequest().getId().equals(request.getId()) && t.getDirection() == PaymentDirection.IN).toList();
            var receipts = linked.stream().filter(t -> t.getStatus() == PaymentStatus.PAID).toList();
            boolean fullyReturned = !receipts.isEmpty() && receipts.stream().allMatch(t ->
                    t.getAppliedAmount() != null && t.getAppliedAmount().signum() == 0
                    && t.getProcessingNote() != null && t.getProcessingNote().startsWith(
                            "RECONCILED_REFUND: RECONCILIATION_REQUIRED: BANK_STATEMENT "));
            boolean pending = linked.stream().anyMatch(t -> t.getStatus() == PaymentStatus.PENDING)
                    || request.getEvidences().stream().anyMatch(e -> e.getStatus() == PaymentEvidenceStatus.PENDING);
            if (fullyReturned && !pending) {
                request.setStatus(PaymentRequestStatus.VOIDED);
                request.setIssueNote("Bank statement exception fully returned through verified manual refunds");
            }
        }
        boolean open = rows.stream().anyMatch(r -> RefundMath.RESERVED.contains(r.getStatus()) && r.getStatus() != RefundStatus.REFUNDED
                || "REPORTED_NOT_RECEIVED".equals(r.getReceiptStatus()));
        open |= transactions.stream().anyMatch(t -> t.getStatus() == PaymentStatus.PENDING
                || t.getDirection() == PaymentDirection.IN && RefundMath.unapplied(t.getProcessingNote()));
        open |= requests.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream().anyMatch(r ->
                Set.of(PaymentRequestStatus.PROOF_SUBMITTED, PaymentRequestStatus.REVIEW_REQUIRED).contains(r.getStatus()));
        open |= disputeQueries.getObject().hasActiveDispute(bookingId);
        var next = open ? BookingStatus.CANCELLATION_REVIEW : BookingStatus.CANCELLED;
        if (booking.getStatus() != next) {
            booking.setStatus(next);
            ledger.saveBooking(booking);
            notifications.changed(booking, "BOOKING_CHANGED");
        }
        return !open;
    }

    @Override
    @Transactional
    public void stopYearlyCollections(Long bookingId) {
        ledger.requireBookingForUpdate(bookingId);
        for (var row : requests.findByBooking_IdOrderByCreatedAtDesc(bookingId)) {
            if (row.getStatus() == PaymentRequestStatus.CONFIRMED) continue;
            boolean proof = row.getEvidences().stream().anyMatch(e -> e.getStatus() == PaymentEvidenceStatus.PENDING);
            row.setStatus(proof || row.getStatus() == PaymentRequestStatus.REVIEW_REQUIRED
                    ? PaymentRequestStatus.REVIEW_REQUIRED : PaymentRequestStatus.VOIDED);
        }
    }
}
