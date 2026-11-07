package com.fptufa26.ecomotion.booking.policy;

import java.math.BigDecimal;

public final class YearlySettlementMath {
    private YearlySettlementMath() {}

    public record Result(BigDecimal obligation, BigDecimal rentRefund, BigDecimal depositRefund, BigDecimal balanceDue) {}

    public static Result settle(BigDecimal obligation, BigDecimal rentPaid, BigDecimal depositHeld, BigDecimal deduction) {
        for (BigDecimal amount : new BigDecimal[]{obligation, rentPaid, depositHeld, deduction}) {
            if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Amounts must be nonnegative");
        }
        BigDecimal debt = obligation.subtract(rentPaid).max(BigDecimal.ZERO);
        if (deduction.compareTo(debt) > 0 || deduction.compareTo(depositHeld) > 0) {
            throw new IllegalArgumentException("Deduction exceeds outstanding debt or deposit");
        }
        return new Result(obligation, rentPaid.subtract(obligation).max(BigDecimal.ZERO),
                depositHeld.subtract(deduction), debt.subtract(deduction));
    }
}
