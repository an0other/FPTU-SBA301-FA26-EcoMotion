package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.vehicle.entity.PlanType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public final class DepositRefundRules {

    public static final BigDecimal FULL = BigDecimal.ONE;
    public static final BigDecimal LATE = new BigDecimal("0.50");

    private static final Duration SHORT_LEAD = Duration.ofHours(24);
    private static final Duration LONG_LEAD = Duration.ofDays(30);

    private DepositRefundRules() {
    }

    public static BigDecimal ratio(PlanType planType, Instant cancelledAt, Instant pickupTime) {
        if (planType == PlanType.YEARLY) {
            return BigDecimal.ZERO;
        }
        Duration lead = Duration.between(cancelledAt, pickupTime);
        Duration milestone = planType == PlanType.MONTHLY ? LONG_LEAD : SHORT_LEAD;
        if (!lead.isNegative() && lead.compareTo(milestone) >= 0) {
            return FULL;
        }
        return LATE;
    }
}
