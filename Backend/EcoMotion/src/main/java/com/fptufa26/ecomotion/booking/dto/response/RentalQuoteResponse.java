package com.fptufa26.ecomotion.booking.dto.response;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record RentalQuoteResponse(boolean available, String reason, Instant searchFrom, Instant searchTo,
                                  List<Window> availableWindows, List<Option> options, List<Window> occupiedWindows) {
    public RentalQuoteResponse(boolean available, String reason, Instant searchFrom, Instant searchTo,
                               List<Window> availableWindows, List<Option> options) {
        this(available, reason, searchFrom, searchTo, availableWindows, options, List.of());
    }
    public record Window(Instant startAt, Instant endAt) {}
    public record Item(Long planId, PlanType planType, int slotLengthMinutes, BigDecimal unitPrice,
                       int quantity) {
        public BigDecimal getAmount() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
    public record Option(Instant plannedEndAt, int coveredHours, Long policyPlanId,
                         BigDecimal rentalAmount, BigDecimal depositAmount, List<Item> items) {}
}
