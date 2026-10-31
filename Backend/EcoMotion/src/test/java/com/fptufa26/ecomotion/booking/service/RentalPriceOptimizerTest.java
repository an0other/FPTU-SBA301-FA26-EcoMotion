package com.fptufa26.ecomotion.booking.service;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class RentalPriceOptimizerTest {
    private RentalPlan plan(long id, int hours, int price) {
        RentalPlan p = new RentalPlan(); p.setId(id);
        p.setPlanType(hours == 1 ? PlanType.HOURLY : hours == 720 ? PlanType.MONTHLY : PlanType.DAILY);
        p.setSlotLengthMinutes(hours * 60); p.setSlotPrice(BigDecimal.valueOf(price)); return p;
    }
    @Test void decomposesFromLargestUnitEvenWhenHoursWouldBeCheaper() {
        var normal = RentalPriceOptimizer.optimize(List.of(plan(1,1,10),plan(2,24,1000)),51).getFirst();
        assertThat(normal.hours()).isEqualTo(51);
        assertThat(normal.items()).extracting(i -> i.quantity()).containsExactly(2,3);
        assertThat(normal.items()).extracting(i -> i.planId()).containsExactly(2L,1L);
        assertThat(normal.amount()).isEqualByComparingTo("2030");
    }
    @Test void keepsFiveHourlySlotsAndOffersOneDayRegardlessOfPrice() {
        var result = RentalPriceOptimizer.optimize(List.of(plan(1,1,100),plan(2,24,400)),5);
        assertThat(result).extracting(RentalPriceOptimizer.Combination::hours).containsExactly(5,24);
        assertThat(result.getFirst().items()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualTo(5));
        assertThat(result.getLast().amount()).isEqualByComparingTo("400");
        assertThat(RentalPriceOptimizer.optimize(List.of(plan(1,1,100),plan(2,24,500)),5)).hasSize(2);
        assertThat(RentalPriceOptimizer.optimize(List.of(plan(1,1,100),plan(2,24,600)),5)).hasSize(2);
    }
    @Test void roundsTwoDaysAndThreeHoursToThreeDaysIfExtraHoursCostMore() {
        var result = RentalPriceOptimizer.optimize(List.of(plan(1,1,400),plan(2,24,1000)),51);
        assertThat(result).extracting(RentalPriceOptimizer.Combination::hours).containsExactly(51,72);
        assertThat(result.getFirst().amount()).isEqualByComparingTo("3200");
        assertThat(result.getLast().amount()).isEqualByComparingTo("3000");
    }
    @Test void decomposesMonthDayHourAndCanRoundUpToMonth() {
        var plans = List.of(plan(1,1,100),plan(2,24,1000),plan(3,720,15000));
        var normal = RentalPriceOptimizer.optimize(plans,771).getFirst();
        assertThat(normal.items()).extracting(i -> i.quantity()).containsExactly(1,2,3);
        var result = RentalPriceOptimizer.optimize(plans,719);
        assertThat(result).extracting(RentalPriceOptimizer.Combination::hours).containsExactly(719,720);
    }
    @Test void exactUnitsZeroPricesAndBoundariesDoNotProduceDuplicateOrOversizedOptions() {
        var plans = List.of(plan(1,1,0),plan(2,24,0));
        assertThat(RentalPriceOptimizer.optimize(plans,24)).extracting(RentalPriceOptimizer.Combination::hours).containsExactly(24,48);
        assertThat(RentalPriceOptimizer.optimize(List.of(),1)).isEmpty();
        assertThat(RentalPriceOptimizer.optimize(plans,0)).isEmpty();
        assertThat(RentalPriceOptimizer.optimize(plans,8641)).isEmpty();
        assertThat(RentalPriceOptimizer.optimize(plans,8640)).hasSize(1);
    }
}
