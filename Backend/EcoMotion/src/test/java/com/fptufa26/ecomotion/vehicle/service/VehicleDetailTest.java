package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class VehicleDetailTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalPlanRepository rentalPlanRepository;

    @Test
    @DisplayName("detail exposes the real vehicle attributes for an approved car")
    void detailExposesRealAttributes() {
        VehicleDetailResponse detail = vehicleService.getVehicleDetail(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.brand()).isEqualTo("VinFast");
        assertThat(detail.model()).isEqualTo("VF 6");
        assertThat(detail.manufactureYear()).isEqualTo(2024);
        assertThat(detail.seatCount()).isEqualTo(5);
        assertThat(detail.transmission()).isEqualTo(Transmission.AUTOMATIC);
        assertThat(detail.fuelType()).isEqualTo(FuelType.EV);
        assertThat(detail.color()).isEqualTo("Tr\u1eafng");
        assertThat(detail.ratingAvg()).isEqualByComparingTo(new BigDecimal("4.70"));
        assertThat(detail.ratingCount()).isEqualTo(24);
    }

    @Test
    @DisplayName("images are returned in display order with the cover first")
    void imagesFollowDisplayOrder() {
        VehicleDetailResponse detail = vehicleService.getVehicleDetail(1L);

        assertThat(detail.imageUrls()).hasSize(3);
        assertThat(detail.imageUrls().get(0)).contains("114343463");
        assertThat(detail.imageUrls().get(1)).contains("114403238");
        assertThat(detail.imageUrls().get(2)).contains("114455179");
    }

    @Test
    @DisplayName("only ACTIVE plans are exposed, in plan id order")
    void onlyActivePlansAreExposed() {
        Vehicle vehicle = vehicleRepository.findById(1L).orElseThrow();

        RentalPlan retired = new RentalPlan();
        retired.setVehicle(vehicle);
        retired.setPlanType(PlanType.YEARLY);
        retired.setSlotLengthMinutes(525600);
        retired.setSlotPrice(new BigDecimal("999000000.00"));
        retired.setStatus(PlanStatus.INACTIVE);
        rentalPlanRepository.saveAndFlush(retired);

        VehicleDetailResponse detail = vehicleService.getVehicleDetail(1L);

        assertThat(detail.plans()).extracting(VehicleDetailResponse.PlanDetail::planType)
                .containsExactly(PlanType.HOURLY, PlanType.DAILY, PlanType.MONTHLY, PlanType.YEARLY);
        assertThat(detail.plans()).extracting(VehicleDetailResponse.PlanDetail::id)
                .doesNotContain(retired.getId());
    }

    @Test
    @DisplayName("plan pricing and policy numbers are the stored values")
    void planNumbersAreStoredValues() {
        VehicleDetailResponse detail = vehicleService.getVehicleDetail(1L);

        VehicleDetailResponse.PlanDetail hourly = detail.plans().stream()
                .filter(plan -> plan.planType() == PlanType.HOURLY)
                .findFirst()
                .orElseThrow();

        assertThat(hourly.id()).isNotNull();
        assertThat(hourly.slotLengthMinutes()).isEqualTo(240);
        assertThat(hourly.slotPrice()).isEqualByComparingTo(new BigDecimal("180000.00"));
        assertThat(hourly.upfrontAmount()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("provider contact details come from the owning provider")
    void providerInfoIsExposed() {
        VehicleDetailResponse detail = vehicleService.getVehicleDetail(1L);

        assertThat(detail.provider()).isNotNull();
        assertThat(detail.provider().displayName()).isEqualTo("EcoMotion Depot A");
        assertThat(detail.provider().providerType()).isEqualTo(ProviderType.BUSINESS);
        assertThat(detail.provider().hotline()).isEqualTo("02873000010");
        assertThat(detail.provider().depotAddress()).contains("Nguy\u1ec5n Hu\u1ec7");
    }

    @Test
    @DisplayName("an unknown vehicle id is reported as not found")
    void unknownIdIsNotFound() {
        assertThatThrownBy(() -> vehicleService.getVehicleDetail(999999L))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    @DisplayName("a vehicle that is not approved is reported as not found")
    void unapprovedVehicleIsNotFound() {
        Vehicle vehicle = vehicleRepository.findById(8L).orElseThrow();
        vehicle.setStatus(VehicleStatus.PENDING);
        vehicleRepository.saveAndFlush(vehicle);

        assertThatThrownBy(() -> vehicleService.getVehicleDetail(8L))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    @DisplayName("the public payload never carries the plate, chassis, engine or documents")
    void publicPayloadHasNoSensitiveFields() {
        List<Class<?>> publicTypes = List.of(
                VehicleCardResponse.class,
                VehicleCardResponse.PlanPrice.class,
                VehicleDetailResponse.class,
                VehicleDetailResponse.ProviderInfo.class,
                VehicleDetailResponse.PlanDetail.class);

        for (Class<?> type : publicTypes) {
            List<String> fields = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
            assertThat(fields)
                    .as("fields of %s", type.getSimpleName())
                    .doesNotContain("licensePlate", "chassisNo", "engineNo", "documents");
        }
    }
}
