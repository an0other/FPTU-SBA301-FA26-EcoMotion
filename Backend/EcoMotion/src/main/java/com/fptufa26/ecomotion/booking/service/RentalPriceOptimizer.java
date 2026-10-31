package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse.Item;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import java.math.BigDecimal;
import java.util.*;

public final class RentalPriceOptimizer {
    public static final int MAX_HOURS = 360 * 24;
    private RentalPriceOptimizer() {}
    public record Combination(int hours, BigDecimal amount, List<Item> items) {}

    public static List<Combination> optimize(List<RentalPlan> input, int requiredHours) {
        if (requiredHours < 1 || requiredHours > MAX_HOURS) return List.of();
        List<RentalPlan> plans = input.stream().sorted(
                Comparator.comparingInt(RentalPlan::getSlotLengthMinutes).reversed()
                        .thenComparing(RentalPlan::getSlotPrice).thenComparing(RentalPlan::getId)).toList();
        List<Combination> result = new ArrayList<>();
        Combination normal = decompose(plans, requiredHours);
        if (normal != null) result.add(normal);
        Set<Integer> covered = new HashSet<>();
        for (RentalPlan plan : plans) {
            int unit = plan.getSlotLengthMinutes() / 60;
            if (unit <= 1) continue;
            int roundedHours = (requiredHours / unit + 1) * unit;
            if (roundedHours > MAX_HOURS || !covered.add(roundedHours)) continue;
            Combination rounded = decompose(plans, roundedHours);
            if (rounded != null)
                result.add(rounded);
        }
        return result;
    }

    private static Combination decompose(List<RentalPlan> plans, int hours) {
        int remainder = hours;
        BigDecimal amount = BigDecimal.ZERO;
        List<Item> items = new ArrayList<>();
        for (RentalPlan plan : plans) {
            int unit = plan.getSlotLengthMinutes() / 60;
            int quantity = remainder / unit;
            if (quantity == 0) continue;
            remainder %= unit;
            Item item = new Item(plan.getId(), plan.getPlanType(), plan.getSlotLengthMinutes(),
                    plan.getSlotPrice(), quantity);
            items.add(item);
            amount = amount.add(item.getAmount());
        }
        return remainder == 0 && !items.isEmpty() ? new Combination(hours, amount, List.copyOf(items)) : null;
    }
}
