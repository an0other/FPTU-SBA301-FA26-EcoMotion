package com.fptufa26.ecomotion.booking.service.implement;
import com.fptufa26.ecomotion.booking.dto.request.*;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse.Window;
import com.fptufa26.ecomotion.schedule.dto.response.OccupiedScheduleWindow;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.*;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RentalPricingServiceTest {
    private final Instant start=Instant.parse("2030-11-01T01:00:00Z");
    private RentalPlan plan(long id,int hours,int price) {
        RentalPlan p=new RentalPlan();p.setId(id);p.setPlanType(hours==1?PlanType.HOURLY:hours==720?PlanType.MONTHLY:PlanType.DAILY);
        p.setSlotLengthMinutes(hours*60);p.setSlotPrice(BigDecimal.valueOf(price));return p;
    }
    @Test void removesUnavailableFourDayOptionAndAllowsItWhenFree() {
        var catalog=mock(VehicleCatalogService.class);var plans=mock(RentalPlanRepository.class);
        var schedule=mock(ScheduleService.class);var deposit=mock(VehicleDepositPolicyService.class);
        Vehicle vehicle=new Vehicle();vehicle.setId(1L);vehicle.setMaintenanceBufferMinutes(30);
        when(catalog.requireApproved(1L)).thenReturn(vehicle);
        when(plans.findByVehicle_IdAndStatusOrderByIdAsc(1L,PlanStatus.ACTIVE))
                .thenReturn(List.of(plan(1,1,700),plan(2,24,1000),plan(3,720,4000)));
        when(deposit.resolve(eq(vehicle),any())).thenAnswer(inv ->
                new VehicleDepositPolicyService.DepositQuote(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));
        var service=new RentalPricingServiceImpl(plans,catalog,deposit,schedule,
                Clock.fixed(start.minus(Duration.ofDays(1)),ZoneOffset.UTC));
        when(schedule.occupiedWindows(anyLong(),any(),any())).thenReturn(List.of(
                new OccupiedScheduleWindow(start.plus(Duration.ofHours(75)),start.plus(Duration.ofHours(100)),ScheduleType.BOOKED)));
        var quote=service.quote(new RentalQuoteRequest(1L,start,start.plus(Duration.ofHours(74))));
        assertThat(quote.available()).isTrue();assertThat(quote.options()).hasSize(1);
        assertThat(quote.options().getFirst().coveredHours()).isEqualTo(74);
        when(schedule.occupiedWindows(anyLong(),any(),any())).thenReturn(List.of());
        quote=service.quote(new RentalQuoteRequest(1L,start,start.plus(Duration.ofHours(74))));
        assertThat(quote.options()).extracting(o -> o.coveredHours()).containsExactly(74,96);
        assertThat(quote.options().getLast().rentalAmount()).isEqualByComparingTo("4000");
        assertThat(quote.options().getFirst().policyPlanId()).isEqualTo(2L);
        when(plans.findByVehicle_IdAndStatusOrderByIdAsc(1L,PlanStatus.ACTIVE))
                .thenReturn(List.of(plan(1,1,100),plan(2,24,1000)));
        quote=service.quote(new RentalQuoteRequest(1L,start,start.plus(Duration.ofHours(74))));
        assertThat(quote.options()).extracting(o -> o.coveredHours()).containsExactly(74,96);
        assertThat(quote.options().getFirst().rentalAmount()).isEqualByComparingTo("3200");
        assertThat(quote.options().getLast().rentalAmount()).isEqualByComparingTo("4000");
        assertThatThrownBy(() -> service.validateSelection(1L,start,start.plus(Duration.ofHours(74)),
                List.of(new PricingItemRequest(2L,4)),new BigDecimal("4000"))).hasMessageContaining("thời lượng");
        when(schedule.occupiedWindows(anyLong(),any(),any())).thenReturn(List.of(
                new OccupiedScheduleWindow(start.plusSeconds(7200),start.plusSeconds(10800),ScheduleType.BOOKED),
                new OccupiedScheduleWindow(start.plusSeconds(75*3600),start.plusSeconds(100*3600),ScheduleType.BOOKED)));
        quote=service.quote(new RentalQuoteRequest(1L,start,start.plus(Duration.ofHours(74))));
        assertThat(quote.available()).isFalse();
        assertThat(quote.options()).isEmpty();
        assertThat(quote.occupiedWindows()).containsExactly(new Window(start.plusSeconds(5400),start.plusSeconds(12600)));
    }
    @Test void mergesBusyWindowsRoundsGapsAndRejectsFractionalHours() {
        var gaps=RentalPricingServiceImpl.gaps(List.of(
                new Window(start,start.plusSeconds(5400)),
                new Window(start.plusSeconds(3600),start.plusSeconds(10800))),start,start.plus(Duration.ofHours(8)),2);
        assertThat(gaps).containsExactly(new Window(start.plus(Duration.ofHours(3)),start.plus(Duration.ofHours(8))));
        assertThatThrownBy(() -> RentalPricingServiceImpl.validateHours(start.plusSeconds(60),start.plusSeconds(7200)))
                .hasMessageContaining("giờ tròn");
    }
}
