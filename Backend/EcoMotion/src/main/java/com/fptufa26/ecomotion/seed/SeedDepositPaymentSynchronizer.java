package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDepositPaymentSynchronizer {
    private final BookingPolicySnapshotRepository snapshots;
    private final PaymentTransactionRepository transactions;
    private final PaymentRequestRepository requests;
    private final ProviderBankAccountRepository banks;
    private final PaymentCodeGenerator codes;

    @Transactional
    public void synchronize(Booking booking) {
        DepositPlan plan;
        try {
            plan = inspectDeposit(booking);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipped demo deposit sync for {}: {}", booking.getBookingCode(), ex.getMessage());
            return;
        }
        applyDeposit(booking, plan);
    }

    private record DepositPlan(PaymentRequest request,
                               List<PaymentTransaction> sources, BigDecimal paid, ProviderBankAccount bank, Instant paidAt) {}

    private DepositPlan inspectDeposit(Booking b) {
        BookingPolicySnapshot snapshot = snapshots.findByBooking_Id(b.getId()).orElseThrow(() -> new IllegalArgumentException("snapshot missing"));
        List<PaymentTransaction> sources = transactions.findByBooking_Id(b.getId()).stream()
                .filter(t -> t.getType() == PaymentType.DEPOSIT_PAY && t.getDirection() == PaymentDirection.IN && t.getStatus() == PaymentStatus.PAID).toList();
        if (sources.isEmpty() || sources.stream().anyMatch(t -> t.getPaidAt() == null || t.getAmount().signum() <= 0
                || RefundMath.unapplied(t.getProcessingNote()) || t.getMethod() != PaymentMethod.BANK_TRANSFER))
            throw new IllegalArgumentException("no unambiguous paid bank deposit");
        BigDecimal paid = sources.stream().map(t -> RefundMath.appliedPortion(t.getAmount(), t.getAppliedAmount(), t.getProcessingNote()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = snapshot.getDepositAmount();
        if (expected == null && snapshot.getSlotPrice() != null && snapshot.getDepositRatio() != null
                && snapshot.getSlotPrice().compareTo(b.getTotalAmount()) == 0) {
            expected = RefundMath.money(snapshot.getSlotPrice().multiply(snapshot.getDepositRatio()));
        }
        if (expected == null || expected.compareTo(paid) != 0)
            throw new IllegalArgumentException("deposit does not match snapshot");
        PaymentRequest request = requests.findByBooking_IdAndPaymentType(b.getId(), PaymentRequestType.DEPOSIT).orElse(null);
        if (request != null && (request.getStatus() != PaymentRequestStatus.CONFIRMED
                || request.getPaidAmount().compareTo(paid) != 0 || request.getAmount().compareTo(paid) != 0))
            throw new IllegalArgumentException("conflicting payment request");
        if (sources.stream().anyMatch(t -> t.getPaymentRequest() != null
                && (request == null || !t.getPaymentRequest().getId().equals(request.getId()))))
            throw new IllegalArgumentException("transaction already linked elsewhere");
        ProviderBankAccount bank = banks.findByProvider_IdAndStatus(b.getVehicle().getProvider().getId(), BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("bank account missing"));
        if (sources.stream().anyMatch(t -> t.getBankAccountNoSnapshot() != null
                && (!t.getBankAccountNoSnapshot().equals(bank.getAccountNumber())
                    || t.getBankCodeSnapshot() != null && !t.getBankCodeSnapshot().equals(bank.getBankCode()))))
            throw new IllegalArgumentException("historical bank differs from demo account");
        Instant paidAt = sources.stream().map(PaymentTransaction::getPaidAt).max(Instant::compareTo).orElseThrow();
        return new DepositPlan(request, sources, paid, bank, paidAt);
    }

    private void applyDeposit(Booking b, DepositPlan plan) {
        PaymentRequest request = plan.request();
        if (request == null) {
            request = new PaymentRequest();
            request.setBooking(b);
            request.setPaymentType(PaymentRequestType.DEPOSIT);
            request.setAmount(plan.paid());
            request.setPaidAmount(plan.paid());
            request.setStatus(PaymentRequestStatus.CONFIRMED);
            request.setPaymentCode(codes.generatePaymentCode("VQ"));
            request.setTransferReference(b.getBookingCode().replace("-", "") + "DEP");
            request.setBankCodeSnapshot(plan.bank().getBankCode());
            request.setBankNameSnapshot(plan.bank().getBankName());
            request.setAccountNumberSnapshot(plan.bank().getAccountNumber());
            request.setAccountHolderSnapshot(plan.bank().getAccountHolderName());
            request.setReceivingBankAccount(plan.bank());
            request.setConfirmedAt(plan.paidAt());
            request.setConfirmedBy(b.getVehicle().getProvider().getUser());
            request.setCreatedAt(plan.sources().stream().map(PaymentTransaction::getCreatedAt).min(Instant::compareTo).orElse(plan.paidAt()));
            request.setExpiresAt(null);
            request = requests.save(request);
        }
        for (PaymentTransaction source : plan.sources()) {
            if (source.getPaymentRequest() == null) source.setPaymentRequest(request);
            if (source.getAppliedAmount() == null || source.getAppliedAmount().signum() == 0)
                source.setAppliedAmount(source.getAmount());
        }
    }

}
