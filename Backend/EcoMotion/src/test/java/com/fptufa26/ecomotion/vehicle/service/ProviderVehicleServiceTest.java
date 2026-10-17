package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProviderVehicleServiceTest {

    @Autowired
    private ProviderVehicleService providerVehicleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @DisplayName("getProviderVehicles returns fleet items with stats")
    void getProviderVehiclesReturnsFleetAndStats() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        ProviderVehiclePageResponse response = providerVehicleService.getProviderVehicles(
                user.getUserId(),
                null,
                null,
                null,
                "newest",
                0,
                10
        );

        assertThat(response).isNotNull();
        assertThat(response.stats()).isNotNull();
        assertThat(response.stats().total()).isGreaterThan(0);
        assertThat(response.items()).isNotEmpty();

        ProviderVehicleItemResponse firstItem = response.items().get(0);
        assertThat(firstItem.id()).isNotNull();
        assertThat(firstItem.licensePlate()).isNotBlank();
        assertThat(firstItem.brand()).isNotBlank();
        assertThat(firstItem.model()).isNotBlank();
        assertThat(firstItem.status()).isNotNull();
    }

    @Test
    @DisplayName("getProviderVehicles filters by keyword and fuel type")
    void getProviderVehiclesFiltersKeywordAndFuel() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        ProviderVehiclePageResponse response = providerVehicleService.getProviderVehicles(
                user.getUserId(),
                "VinFast",
                FuelType.EV,
                null,
                "newest",
                0,
                10
        );

        assertThat(response).isNotNull();
        for (ProviderVehicleItemResponse item : response.items()) {
            assertThat(item.brand()).isEqualTo("VinFast");
            assertThat(item.fuelType()).isEqualTo(FuelType.EV);
        }
    }

    @Test
    @DisplayName("toggleVehicleVisibility switches between APPROVED and HIDDEN")
    void toggleVehicleVisibilitySuccess() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();

        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).stream()
                .filter(v -> v.getStatus() == VehicleStatus.APPROVED)
                .findFirst()
                .orElseThrow();

        ProviderVehicleItemResponse hiddenItem = providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                vehicle.getId(),
                new VehicleVisibilityRequest(false)
        );
        assertThat(hiddenItem.status()).isEqualTo(VehicleStatus.HIDDEN);

        ProviderVehicleItemResponse approvedItem = providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                vehicle.getId(),
                new VehicleVisibilityRequest(true)
        );
        assertThat(approvedItem.status()).isEqualTo(VehicleStatus.APPROVED);
    }

    @Test
    @DisplayName("toggleVehicleVisibility rejects pending and rejected vehicles")
    void toggleVehicleVisibilityRejectsPendingAndRejected() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();

        Vehicle pendingVehicle = new Vehicle();
        pendingVehicle.setProvider(provider);
        pendingVehicle.setLicensePlate("51H-" + UUID.randomUUID().toString().substring(0, 5));
        pendingVehicle.setChassisNo("CHASSIS-" + UUID.randomUUID().toString().substring(0, 8));
        pendingVehicle.setEngineNo("ENGINE-" + UUID.randomUUID().toString().substring(0, 8));
        pendingVehicle.setBrand("VinFast");
        pendingVehicle.setModel("VF 8");
        pendingVehicle.setManufactureYear(2024);
        pendingVehicle.setSeatCount(5);
        pendingVehicle.setTransmission(Transmission.AUTOMATIC);
        pendingVehicle.setFuelType(FuelType.EV);
        pendingVehicle.setColor("Đỏ");
        pendingVehicle.setStatus(VehicleStatus.PENDING);
        pendingVehicle.setMaintenanceBufferMinutes(60);
        pendingVehicle.setRatingAvg(BigDecimal.ZERO);
        pendingVehicle.setRatingCount(0);
        vehicleRepository.save(pendingVehicle);

        assertThatThrownBy(() -> providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                pendingVehicle.getId(),
                new VehicleVisibilityRequest(true)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Xe đang chờ duyệt");

        pendingVehicle.setStatus(VehicleStatus.REJECTED);
        vehicleRepository.save(pendingVehicle);

        assertThatThrownBy(() -> providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                pendingVehicle.getId(),
                new VehicleVisibilityRequest(true)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Xe bị từ chối hồ sơ");
    }

    @Test
    @DisplayName("toggleVehicleVisibility throws VehicleNotFoundException for alien vehicle")
    void toggleVehicleVisibilityRejectsAlienVehicle() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        assertThatThrownBy(() -> providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                999999L,
                new VehicleVisibilityRequest(true)
        )).isInstanceOf(VehicleNotFoundException.class);
    }
}
