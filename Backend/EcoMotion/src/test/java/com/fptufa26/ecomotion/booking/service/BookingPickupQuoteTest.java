package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.PickupQuoteRequest;
import com.fptufa26.ecomotion.booking.service.implement.BookingServiceImpl;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.location.service.MapLocationService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPickupQuoteTest {
    @Mock private VehicleCatalogService vehicleCatalogService;
    @Mock private MapLocationService mapLocationService;
    @InjectMocks private BookingServiceImpl service;

    private final PickupQuoteRequest request = new PickupQuoteRequest(1L, 2L,
            new BigDecimal("10.9"), new BigDecimal("106.8"));
    private Provider provider;
    private RentalPlan plan;

    @BeforeEach
    void setUp() {
        provider = new Provider();
        provider.setDepotLatitude(new BigDecimal("10.7"));
        provider.setDepotLongitude(new BigDecimal("106.6"));
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        plan = new RentalPlan();
        plan.setPlanType(PlanType.DAILY);
        plan.setFreeRadiusKm(10);
        plan.setOutOfRadiusRate(new BigDecimal("10000"));
        when(vehicleCatalogService.requireApproved(1L)).thenReturn(vehicle);
        when(vehicleCatalogService.requireActivePlan(1L, 2L)).thenReturn(plan);
    }

    @Test
    void onlyExcessKilometersAreRoundedUpAndChargedOnce() {
        double[] distances = {0, 9.99, 10, 10.01, 11, 11.01, 12.2};
        int[] chargeable = {0, 0, 0, 1, 1, 2, 3};
        for (int i = 0; i < distances.length; i++) {
            when(mapLocationService.distanceKm(10.7, 106.6, 10.9, 106.8)).thenReturn(distances[i]);
            var quote = service.quotePickup(request);
            assertThat(quote.distanceKm()).isEqualTo(distances[i]);
            assertThat(quote.chargeableKm()).isEqualTo(chargeable[i]);
            assertThat(quote.pickupFee()).isEqualByComparingTo(BigDecimal.valueOf(chargeable[i] * 10000L));
        }
    }

    @Test
    void missingDepotOrInvalidPolicyDoesNotSilentlyProduceFreePickup() {
        provider.setDepotLatitude(null);
        assertThatThrownBy(() -> service.quotePickup(request)).isInstanceOf(ConflictException.class);
        provider.setDepotLatitude(new BigDecimal("10.7"));
        plan.setFreeRadiusKm(null);
        assertThatThrownBy(() -> service.quotePickup(request)).isInstanceOf(ConflictException.class);
        plan.setFreeRadiusKm(10);
        plan.setOutOfRadiusRate(new BigDecimal("-1"));
        assertThatThrownBy(() -> service.quotePickup(request)).isInstanceOf(ConflictException.class);
    }
}
