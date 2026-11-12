package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.enums.RefundStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

public final class RefundMath {

    public static final Set<RefundStatus> RESERVED = Set.of(
            RefundStatus.REQUESTED,
            RefundStatus.UNDER_REVIEW,
            RefundStatus.APPROVED,
            RefundStatus.PROCESSING,
            RefundStatus.PENDING_RECONCILIATION,
            RefundStatus.REFUNDED);

    private RefundMath() {
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal appliedPortion(BigDecimal amount, BigDecimal applied, String processingNote) {
        if (applied == null || (applied.signum() == 0 && !unapplied(processingNote) && !excess(processingNote))) {
            return money(amount);
        }
        return money(applied);
    }

    public static final String EXCESS_PREFIX = "EXCESS";
    public static final String OVERPAY_EVENT = "OVERPAY:";
    public static final String MISMATCH_EVENT = "MISMATCH:";

    public static boolean excess(String processingNote) {
        return processingNote != null && processingNote.startsWith(EXCESS_PREFIX);
    }

    public static boolean excessEvent(String sourceEventId) {
        return sourceEventId != null && (sourceEventId.startsWith(OVERPAY_EVENT) || sourceEventId.startsWith(MISMATCH_EVENT));
    }

    public static boolean unapplied(String processingNote) {
        return processingNote != null && (processingNote.startsWith("RECONCILIATION_REQUIRED")
                || processingNote.startsWith("LATE_IPN"));
    }

    public static BigDecimal openAllocated(BigDecimal allocated, BigDecimal obligation,
                                           BigDecimal refundedAllocated, BigDecimal reservedAllocated) {
        return money(allocated).subtract(money(obligation)).subtract(money(refundedAllocated))
                .subtract(money(reservedAllocated)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean countsTowardAllocatedRefund(String sourceEventId) {
        if (sourceEventId == null) {
            return true;
        }
        return !sourceEventId.startsWith("EXCESS:") && !sourceEventId.startsWith("LATE_IPN:") && !excessEvent(sourceEventId);
    }

    public static String partId(String sourceEventId, Long transactionId) {
        return sourceEventId + ":TX:" + transactionId;
    }

    public static String vnpayRefundType(BigDecimal refundAmount, BigDecimal transactionAmount,
                                         BigDecimal alreadyUsedOnTransaction) {
        BigDecimal used = money(alreadyUsedOnTransaction);
        if (used.signum() > 0) {
            return "03";
        }
        if (money(refundAmount).compareTo(money(transactionAmount)) == 0) {
            return "02";
        }
        return "03";
    }

    public static RefundStatus mapGatewayResult(String responseCode, String transactionStatus,
                                                boolean checksumValid, boolean timedOut) {
        if (timedOut || !checksumValid || "98".equals(responseCode) || "94".equals(responseCode)
                || "95".equals(responseCode)) {
            return RefundStatus.PENDING_RECONCILIATION;
        }
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            return RefundStatus.REFUNDED;
        }
        if ("00".equals(responseCode) && ("05".equals(transactionStatus) || "06".equals(transactionStatus))) {
            return RefundStatus.PROCESSING;
        }
        if ("09".equals(transactionStatus) || "04".equals(responseCode) || "13".equals(responseCode)
                || "93".equals(responseCode) || "97".equals(responseCode)) {
            return RefundStatus.FAILED;
        }
        return RefundStatus.PENDING_RECONCILIATION;
    }
}
