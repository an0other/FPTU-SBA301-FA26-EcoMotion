package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class VehicleMapTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalPlanRepository rentalPlanRepository;

    private List<VehicleMapResponse> find(BigDecimal latitude, int radiusKm,
                                          String location, String fuelType, BigDecimal maxPrice) {
        return vehicleService.findNearbyVehicles(latitude, new BigDecimal("106.703600"),
                radiusKm, null, location, fuelType, "HOURLY", maxPrice, VehicleSort.DEFAULT);
    }

    @Test
    void radiusAndExistingFiltersAreCombined() {
        assertThat(find(new BigDecimal("10.673300"), 10, null, null, null)).isEmpty();
        assertThat(find(new BigDecimal("10.673300"), 15, null, null, null)).hasSize(8);
        assertThat(find(new BigDecimal("10.773300"), 10, "Nguy", "EV",
                new BigDecimal("200000")))
                .extracting(result -> result.vehicle().id())
                .containsExactly(1L, 2L);
    }

    @Test
    void missingProviderCoordinatesExcludeOnlyItsVehiclesFromMap() {
        Provider provider = providerRepository.findByUser_Id(2L).orElseThrow();
        provider.setDepotLatitude(null);
        provider.setDepotLongitude(null);
        providerRepository.saveAndFlush(provider);

        assertThat(find(new BigDecimal("10.773300"), 15, null, null, null))
                .hasSize(4)
                .allSatisfy(result -> assertThat(result.vehicle().providerId()).isNotEqualTo(provider.getId()));
        assertThat(vehicleService.listRentableVehicles(null, null, null, null, null,
                VehicleSort.DEFAULT, 0, 100).total()).isEqualTo(8);
    }

    @Test
    void invalidPositionOrRadiusIsRejected() {
        assertThatThrownBy(() -> find(new BigDecimal("91"), 10, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> find(new BigDecimal("10.773300"), 12, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unapprovedVehicleAndVehicleWithoutActivePlansAreExcluded() {
        var unapproved = vehicleRepository.findById(1L).orElseThrow();
        unapproved.setStatus(VehicleStatus.HIDDEN);
        vehicleRepository.saveAndFlush(unapproved);

        var inactivePlans = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(2L);
        inactivePlans.forEach(plan -> plan.setStatus(PlanStatus.INACTIVE));
        rentalPlanRepository.saveAllAndFlush(inactivePlans);

        assertThat(find(new BigDecimal("10.773300"), 15, null, null, null))
                .extracting(result -> result.vehicle().id())
                .doesNotContain(1L, 2L)
                .hasSize(6);
    }

    @Test
    void distanceFilterIncludesNearSideAndExcludesFarSideOfTenKilometers() {
        double providerLatitude = 10.7733;
        double degreesForNearSide = Math.toDegrees(9.99 / 6371.0088);
        double degreesForFarSide = Math.toDegrees(10.01 / 6371.0088);

        assertThat(find(BigDecimal.valueOf(providerLatitude - degreesForNearSide),
                10, "Nguy", null, null)).hasSize(4);
        assertThat(find(BigDecimal.valueOf(providerLatitude - degreesForFarSide),
                10, "Nguy", null, null)).isEmpty();
    }
}
