package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.*;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse.*;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.common.exception.*;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.*;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RentalPricingServiceImpl implements RentalPricingService {
    private final RentalPlanRepository planRepository;
    private final VehicleCatalogService vehicleCatalogService;
    private final VehicleDepositPolicyService depositPolicy;
    private final ScheduleService scheduleService;
    private final Clock clock;

    public static int validateHours(Instant start, Instant end) {
        if (start == null || end == null || !start.equals(start.truncatedTo(ChronoUnit.HOURS))
                || !end.equals(end.truncatedTo(ChronoUnit.HOURS)) || !end.isAfter(start)
                || Duration.between(start, end).toHours() > RentalPriceOptimizer.MAX_HOURS)
            throw new BadRequestException("Chọn giờ tròn, thời lượng từ 1 giờ đến 360 ngày");
        return Math.toIntExact(Duration.between(start, end).toHours());
    }

    private List<RentalPlan> plans(Long vehicleId) {
        return planRepository.findByVehicle_IdAndStatusOrderByIdAsc(vehicleId, PlanStatus.ACTIVE).stream()
                .filter(p -> p.getPlanType() != PlanType.YEARLY)
                .filter(p -> p.getSlotLengthMinutes() != null && p.getSlotLengthMinutes() >= 60
                        && p.getSlotLengthMinutes() % 60 == 0 && p.getSlotPrice() != null
                        && p.getSlotPrice().signum() >= 0).toList();
    }

    @Override public RentalQuoteResponse quote(RentalQuoteRequest request) {
        int hours = validateHours(request.pickupTime(), request.requestedEndAt());
        if (request.pickupTime().isBefore(clock.instant().plus(Duration.ofHours(3))))
            throw new BadRequestException("Đặt xe trước giờ nhận ít nhất 3 giờ");
        Vehicle vehicle = vehicleCatalogService.requireApproved(request.vehicleId());
        Instant start = request.pickupTime();
        Instant searchTo = start.plus(Duration.ofDays(30));
        List<Window> occupied = occupied(vehicle, start, start.plus(Duration.ofHours(RentalPriceOptimizer.MAX_HOURS)));
        boolean available = free(start, request.requestedEndAt(), occupied);
        var combinations = RentalPriceOptimizer.optimize(plans(vehicle.getId()), hours).stream()
                .filter(c -> free(start, start.plus(Duration.ofHours(c.hours())), occupied)).toList();
        List<Option> options = new ArrayList<>();
        combinations.stream().filter(c -> c.hours() == hours).findFirst()
                .ifPresent(c -> options.add(option(vehicle, start, c.hours(), c.amount(), c.items())));
        combinations.stream().filter(c -> c.hours() > hours)
                .min(Comparator.comparingInt(RentalPriceOptimizer.Combination::hours)
                        .thenComparing(RentalPriceOptimizer.Combination::amount))
                .ifPresent(c -> options.add(option(vehicle, start, c.hours(), c.amount(), c.items())));
        String reason = !available ? "Xe bận trong khoảng đã chọn"
                : options.isEmpty() ? "Không có tổ hợp gói phù hợp với lịch xe" : null;
        return new RentalQuoteResponse(available, reason, start, searchTo,
                available ? List.of() : gaps(occupied, start, searchTo, hours), options,
                occupied.stream().filter(w -> start.isBefore(w.endAt()) && request.requestedEndAt().isAfter(w.startAt()))
                        .toList());
    }

    private List<Window> occupied(Vehicle vehicle, Instant from, Instant to) {
        Duration buffer = Duration.ofMinutes(Math.max(0, vehicle.getMaintenanceBufferMinutes() == null
                ? 0 : vehicle.getMaintenanceBufferMinutes()));
        return scheduleService.occupiedWindows(vehicle.getId(), from.minus(buffer), to.plus(buffer)).stream()
                .map(s -> new Window(s.startAt().minus(buffer), s.endAt().plus(buffer))).toList();
    }
    private static boolean free(Instant start, Instant end, List<Window> occupied) {
        return occupied.stream().noneMatch(w -> start.isBefore(w.endAt()) && end.isAfter(w.startAt()));
    }
    static List<Window> gaps(List<Window> occupied, Instant from, Instant to, int hours) {
        List<Window> result = new ArrayList<>();
        Instant cursor = from;
        for (Window busy : occupied.stream().sorted(Comparator.comparing(Window::startAt)).toList()) {
            if (!busy.endAt().isAfter(cursor)) continue;
            addGap(result, cursor, busy.startAt().isBefore(to) ? busy.startAt() : to, hours);
            if (busy.endAt().isAfter(cursor)) cursor = busy.endAt();
            if (!cursor.isBefore(to)) break;
        }
        addGap(result, cursor, to, hours);
        return result.stream().limit(3).toList();
    }
    private static void addGap(List<Window> result, Instant start, Instant end, int hours) {
        Instant roundedStart = start.truncatedTo(ChronoUnit.HOURS);
        if (roundedStart.isBefore(start)) roundedStart = roundedStart.plus(1, ChronoUnit.HOURS);
        Instant roundedEnd = end.truncatedTo(ChronoUnit.HOURS);
        if (!roundedEnd.isBefore(roundedStart.plus(Duration.ofHours(hours))))
            result.add(new Window(roundedStart, roundedEnd));
    }
    private Option option(Vehicle vehicle, Instant start, int hours, BigDecimal amount, List<Item> items) {
        Item policy = items.stream().sorted(Comparator.comparingInt(Item::slotLengthMinutes).reversed()
                .thenComparing(Item::planId)).findFirst().orElseThrow();
        return new Option(start.plus(Duration.ofHours(hours)), hours, policy.planId(), amount,
                depositPolicy.resolve(vehicle, amount).amount(), items);
    }
    @Override public Option validateSelection(Long vehicleId, Instant start, Instant end,
                                               List<PricingItemRequest> selection, BigDecimal expected) {
        int hours = validateHours(start, end);
        if (selection == null || selection.isEmpty() || selection.size() > 100 || expected == null)
            throw new BadRequestException("Vui lòng chọn phương án giá");
        Map<Long, RentalPlan> active = new HashMap<>();
        plans(vehicleId).forEach(p -> active.put(p.getId(), p));
        List<Item> items = new ArrayList<>(); Set<Long> seen = new HashSet<>();
        long minutes = 0; BigDecimal amount = BigDecimal.ZERO;
        for (PricingItemRequest entry : selection) {
            if (entry == null || entry.planId() == null || entry.quantity() == null || entry.quantity() < 1
                    || entry.quantity() > RentalPriceOptimizer.MAX_HOURS || !seen.add(entry.planId()))
                throw new BadRequestException("Tổ hợp gói không hợp lệ");
            RentalPlan plan = active.get(entry.planId());
            if (plan == null) throw new ConflictException("Gói giá đã thay đổi, vui lòng lấy báo giá mới");
            Item item = new Item(plan.getId(), plan.getPlanType(), plan.getSlotLengthMinutes(),
                    plan.getSlotPrice(), entry.quantity());
            items.add(item); minutes += (long) item.slotLengthMinutes() * item.quantity();
            amount = amount.add(item.getAmount());
        }
        if (minutes != hours * 60L || amount.compareTo(expected) != 0)
            throw new ConflictException("Giá hoặc thời lượng đã thay đổi, vui lòng lấy báo giá mới");
        return option(vehicleCatalogService.requireApproved(vehicleId), start, hours, amount, items);
    }
}
