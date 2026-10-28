package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.location.service.MapLocationService;
import com.fptufa26.ecomotion.user.dto.ProviderSummary;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.vehicle.dto.RentalPlanData;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.implement.VehicleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceBoundaryTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleImageService vehicleImageService;

    @Mock
    private RentalPlanService rentalPlanService;

    @Mock
    private UserService userService;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private MapLocationService mapLocationService;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleServiceImpl(
                vehicleRepository, vehicleImageService, rentalPlanService, userService, scheduleService,
                mapLocationService);
    }

    @Test
    void listFiltersAndMapsProviderDataFromServiceWithOneLookupPerProvider() {
        Vehicle first = vehicle(11L);
        Vehicle second = vehicle(12L);
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED))
                .thenReturn(List.of(first, second));
        when(userService.findProvidersByIds(List.of(7L))).thenReturn(Map.of(7L, provider()));
        when(vehicleImageService.findImageUrlsByVehicleIds(List.of(11L, 12L)))
                .thenReturn(Map.of(11L, List.of("cover.jpg", "side.jpg")));
        when(rentalPlanService.findActivePlansByVehicleIds(List.of(11L, 12L)))
                .thenReturn(Map.of(11L, List.of(plan()), 12L, List.of(plan())));

        var result = service.listRentableVehicles("service depot", "district", null, "HOURLY",
                new BigDecimal("100"), VehicleSort.DEFAULT, 0, 10);

        assertThat(result.items()).extracting(VehicleCardResponse::id).containsExactly(11L, 12L);
        assertThat(result.items()).allSatisfy(card -> {
            assertThat(card.providerId()).isEqualTo(7L);
            assertThat(card.providerName()).isEqualTo("Service Depot");
            assertThat(card.providerDepotAddress()).isEqualTo("District 1");
        });
        assertThat(result.items().getFirst().imageUrls()).containsExactly("cover.jpg", "side.jpg");
        assertThat(result.items().getFirst().coverImageUrl()).isEqualTo("cover.jpg");
        assertThat(result.items().getLast().imageUrls()).isEmpty();
        verify(userService).findProvidersByIds(List.of(7L));
        verify(first, never()).getProvider();
        verify(second, never()).getProvider();
    }

    @Test
    void mapUsesCoordinatesFromProviderServiceAndLoadsVehiclesOnce() {
        when(mapLocationService.distanceKm(10.7733, 106.7036, 10.7733, 106.7036)).thenReturn(2.5);
        Vehicle vehicle = vehicle(11L);
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED)).thenReturn(List.of(vehicle));
        when(userService.findProvidersByIds(List.of(7L))).thenReturn(Map.of(7L, provider()));
        when(vehicleImageService.findImageUrlsByVehicleIds(List.of(11L))).thenReturn(Map.of());
        when(rentalPlanService.findActivePlansByVehicleIds(List.of(11L)))
                .thenReturn(Map.of(11L, List.of(plan())));

        var results = service.findNearbyVehicles(new BigDecimal("10.773300"),
                new BigDecimal("106.703600"), 10, null, null, null, null, null, VehicleSort.DEFAULT);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().distanceKm()).isEqualTo(2.5);
        verify(mapLocationService).distanceKm(10.7733, 106.7036, 10.7733, 106.7036);
        verify(vehicleRepository).findByStatusOrderByIdAsc(VehicleStatus.APPROVED);
        verify(userService).findProvidersByIds(List.of(7L));
        verify(vehicle, never()).getProvider();
    }

    @Test
    void detailPreservesPlanPoliciesAndProviderDataAcrossServiceBoundary() {
        Vehicle vehicle = vehicle(11L);
        when(vehicleRepository.findByIdAndStatus(11L, VehicleStatus.APPROVED)).thenReturn(Optional.of(vehicle));
        when(vehicleImageService.findImageUrls(11L)).thenReturn(List.of("cover.jpg", "side.jpg"));
        when(rentalPlanService.findActivePlans(11L)).thenReturn(List.of(plan()));
        when(userService.findProvidersByIds(List.of(7L))).thenReturn(Map.of(7L, provider()));

        var detail = service.getVehicleDetail(11L);

        assertThat(detail.imageUrls()).containsExactly("cover.jpg", "side.jpg");
        assertThat(detail.provider().displayName()).isEqualTo("Service Depot");
        assertThat(detail.provider().providerType()).isEqualTo(ProviderType.BUSINESS);
        assertThat(detail.provider().hotline()).isEqualTo("02870000000");
        assertThat(detail.plans()).singleElement().satisfies(actual ->
                assertThat(actual).usingRecursiveComparison().isEqualTo(plan()));
        verify(vehicle, never()).getProvider();
    }

    @Test
    void emptyFleetDoesNotQueryOtherServices() {
        when(vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED)).thenReturn(List.of());

        assertThat(service.listRentableVehicles(null, null, null, null, null,
                VehicleSort.DEFAULT, 0, 10).items()).isEmpty();

        verifyNoInteractions(vehicleImageService, rentalPlanService, userService);
    }

    private Vehicle vehicle(Long id) {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(id);
        when(vehicle.getProviderId()).thenReturn(7L);
        return vehicle;
    }

    private ProviderSummary provider() {
        return new ProviderSummary(7L, "Service Depot", ProviderType.BUSINESS, "02870000000",
                "District 1", new BigDecimal("10.773300"), new BigDecimal("106.703600"),
                new BigDecimal("4.5"), 12);
    }

    private RentalPlanData plan() {
        return new RentalPlanData(PlanType.HOURLY, 240, new BigDecimal("100"), new BigDecimal("500"),
                15, new BigDecimal("0.25"), 10, new BigDecimal("20"),
                5, new BigDecimal("30"), new BigDecimal("0.1"), 480);
    }
}
