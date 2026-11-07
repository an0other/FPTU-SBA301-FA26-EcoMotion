package com.fptufa26.ecomotion.booking.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class YearlyRentalRules {
    private static final int MONTHS = 12;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private YearlyRentalRules() {}

    public static Instant contractEnd(Instant start) {
        return start.atZone(VIETNAM).plusMonths(MONTHS).toInstant();
    }

    public static Instant reservationEnd(Instant contractEnd) {
        return contractEnd.atZone(VIETNAM).toLocalDate().plusDays(1).atStartOfDay(VIETNAM).toInstant();
    }

    public static LocalDate monthBoundary(LocalDate start, int month) {
        if (month < 0 || month > MONTHS) throw new IllegalArgumentException("Invalid month index");
        return start.plusMonths(month);
    }

    public static List<BigDecimal> installments(BigDecimal total, BigDecimal unit) {
        requirePositive(total);
        requirePositive(unit);
        if (total.remainder(unit).signum() != 0) throw new IllegalArgumentException("Total must be a multiple of rounding unit");
        BigDecimal units = total.divide(unit);
        BigDecimal baseUnits = units.divide(BigDecimal.valueOf(MONTHS), 0, RoundingMode.DOWN);
        if (baseUnits.signum() == 0) throw new IllegalArgumentException("Each month must be positive");
        int extraMonths = units.remainder(BigDecimal.valueOf(MONTHS)).intValueExact();
        List<BigDecimal> result = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            result.add(baseUnits.add(i < extraMonths ? BigDecimal.ONE : BigDecimal.ZERO).multiply(unit));
        }
        return List.copyOf(result);
    }

    public static int startedMonths(LocalDate start, LocalDate returned) {
        if (returned.isBefore(start)) throw new IllegalArgumentException("Return precedes handover");
        for (int i = 1; i <= MONTHS; i++) {
            if (returned.isBefore(monthBoundary(start, i))) return i;
        }
        return MONTHS;
    }

    public static BigDecimal earlyTerminationFee(BigDecimal total, BigDecimal earned, BigDecimal unit) {
        requirePositive(total);
        requirePositive(unit);
        if (earned == null || earned.signum() < 0 || earned.compareTo(total) > 0) {
            throw new IllegalArgumentException("Invalid earned rent");
        }
        BigDecimal roundedMonth = total.divide(unit.multiply(BigDecimal.valueOf(MONTHS)), 0, RoundingMode.CEILING).multiply(unit);
        return roundedMonth.min(total.subtract(earned));
    }

    public static BigDecimal lateFee(LocalDate due, LocalDate returned, int graceDays, BigDecimal dailyRate) {
        if (graceDays < 0 || dailyRate == null || dailyRate.signum() < 0) throw new IllegalArgumentException("Invalid late policy");
        long chargedDays = Math.max(0, ChronoUnit.DAYS.between(due, returned) - graceDays);
        return dailyRate.multiply(BigDecimal.valueOf(chargedDays));
    }

    private static void requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
    }
}
