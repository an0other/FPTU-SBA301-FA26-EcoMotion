package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingPaymentStateService;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingPaymentStateServiceImpl implements BookingPaymentStateService {
    private final BookingPaymentLedgerService ledger;

    @Override
    public State evaluate(Booking booking, List<PaymentTransaction> transactions) {
        BigDecimal paid = transactions.stream().filter(this::applied)
                .map(this::allocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = sumRefunds(transactions, PaymentStatus.PAID);
        BigDecimal pendingRefund = sumRefunds(transactions, PaymentStatus.PENDING);
        BigDecimal extras = ledger.listCharges(booking.getId()).stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.BASE_RENTAL && c.getChargeType() != ChargeType.DEPOSIT)
                .map(BookingCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal obligation = booking.getTotalAmount().add(extras);
        boolean closed = Set.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.CANCELLATION_REVIEW).contains(booking.getStatus());
        BigDecimal remaining = closed ? BigDecimal.ZERO : obligation.subtract(paid.subtract(refunded)).max(BigDecimal.ZERO);
        boolean reconcile = transactions.stream().anyMatch(t -> t.getProcessingNote() != null
                && t.getProcessingNote().startsWith("RECONCILIATION_REQUIRED")
                || t.getDirection() == PaymentDirection.IN && t.getStatus() == PaymentStatus.PAID
                    && t.getVerificationMethod() != PaymentVerificationMethod.MOCK_SIMULATED && !applied(t));
        BigDecimal deposit = transactions.stream().filter(this::applied)
                .filter(t -> t.getType() == PaymentType.DEPOSIT_PAY || t.getType() == PaymentType.UPFRONT_PAY)
                .map(this::allocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        var snapshot = ledger.findSnapshot(booking.getId()).orElse(null);
        BigDecimal requiredDeposit = snapshot == null ? null : snapshot.getDepositAmount();
        if (requiredDeposit == null && snapshot != null && snapshot.getDepositRatio() != null)
            requiredDeposit = booking.getTotalAmount().multiply(snapshot.getDepositRatio());
        if (snapshot != null && snapshot.getPlanType() == com.fptufa26.ecomotion.vehicle.enums.PlanType.YEARLY)
            requiredDeposit = snapshot.getUpfrontAmount();
        boolean depositPaid = requiredDeposit != null && requiredDeposit.signum() > 0 && deposit.compareTo(requiredDeposit) >= 0;
        boolean settlementPaid = transactions.stream().filter(this::applied).anyMatch(t -> t.getType() == PaymentType.SETTLEMENT);
        boolean fullyPaid = paid.signum() > 0 && remaining.signum() == 0 && (settlementPaid || booking.getStatus() == BookingStatus.COMPLETED);
        boolean verifying = transactions.stream().anyMatch(pending -> pending.getMethod() == PaymentMethod.VNPAY
                && pending.getDirection() == PaymentDirection.IN && pending.getStatus() == PaymentStatus.PENDING
                && !fullyPaid && (Set.of(PaymentType.DEPOSIT_PAY, PaymentType.UPFRONT_PAY).contains(pending.getType()) && !depositPaid
                    || pending.getType() == PaymentType.SETTLEMENT && booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT));
        BookingPaymentStatus status;
        if (reconcile) status = BookingPaymentStatus.RECONCILIATION_REQUIRED;
        else if (pendingRefund.signum() > 0) status = BookingPaymentStatus.REFUND_PENDING;
        else if (refunded.signum() > 0 && paid.compareTo(refunded) <= 0) status = BookingPaymentStatus.REFUNDED;
        else if (verifying) status = BookingPaymentStatus.VERIFYING;
        else if (fullyPaid)
            status = BookingPaymentStatus.FULLY_PAID;
        else if (depositPaid) status = BookingPaymentStatus.DEPOSIT_PAID;
        else if (paid.signum() > 0) status = BookingPaymentStatus.PARTIALLY_PAID;
        else if (transactions.stream().anyMatch(t -> t.getDirection() == PaymentDirection.IN && t.getStatus() == PaymentStatus.FAILED))
            status = BookingPaymentStatus.FAILED;
        else status = BookingPaymentStatus.UNPAID;
        return new State(status, paid, refunded, pendingRefund, remaining);
    }

    private boolean applied(PaymentTransaction transaction) {
        return transaction.getStatus() == PaymentStatus.PAID && transaction.getDirection() == PaymentDirection.IN
                && (transaction.getProcessingNote() == null || !transaction.getProcessingNote().startsWith("RECONCILIATION_REQUIRED"))
                && transaction.getVerificationMethod() != null
                && Set.of(PaymentVerificationMethod.GATEWAY_VERIFIED, PaymentVerificationMethod.PROVIDER_CONFIRMED,
                    PaymentVerificationMethod.ADMIN_RECONCILED).contains(transaction.getVerificationMethod())
                && allocated(transaction).signum() > 0;
    }

    private BigDecimal allocated(PaymentTransaction transaction) {
        return transaction.getAppliedAmount() == null ? BigDecimal.ZERO : transaction.getAppliedAmount();
    }

    private BigDecimal sumRefunds(List<PaymentTransaction> transactions, PaymentStatus status) {
        return transactions.stream().filter(t -> t.getDirection() == PaymentDirection.OUT && t.getType() == PaymentType.REFUND
                && t.getStatus() == status && t.getVerificationMethod() != PaymentVerificationMethod.MOCK_SIMULATED)
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
