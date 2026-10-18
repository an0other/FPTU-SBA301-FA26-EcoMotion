package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateRentalPlansRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderRentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
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
import java.util.List;
import java.util.Random;
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

    @Test
    @DisplayName("createVehicle creates a new vehicle in PENDING status with zero rating and cover image")
    void createVehicleCreatesPendingVehicle() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        int randPlate = 10000 + new Random().nextInt(90000);

        CreateVehicleRequest request =
                new CreateVehicleRequest(
                        "CHASSIS-" + uniqueSuffix,
                        "ENGINE-" + uniqueSuffix,
                        "51K-" + randPlate,
                        "VinFast",
                        "VF 8 Plus",
                        2024,
                        5,
                        Transmission.AUTOMATIC,
                        FuelType.EV,
                        "Xanh Lục Bảo",
                        60,
                        "https://example.com/cover.png"
                );

        ProviderVehicleDetailResponse response =
                providerVehicleService.createVehicle(user.getUserId(), request);

        assertThat(response.id()).isNotNull();
        assertThat(response.licensePlate()).isEqualTo(request.licensePlate().toUpperCase());
        assertThat(response.status()).isEqualTo(VehicleStatus.PENDING);
        assertThat(response.ratingAvg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.ratingCount()).isEqualTo(0);
        assertThat(response.coverImageUrl()).isEqualTo("https://example.com/cover.png");
    }

    @Test
    @DisplayName("createVehicle throws IllegalArgumentException on duplicate license plate")
    void createVehicleRejectsDuplicateLicensePlate() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        int randPlate = 10000 + new Random().nextInt(90000);

        CreateVehicleRequest request1 =
                new CreateVehicleRequest(
                        "CHASSIS1-" + uniqueSuffix,
                        "ENGINE1-" + uniqueSuffix,
                        "51A-" + randPlate,
                        "VinFast",
                        "VF 8",
                        2024,
                        5,
                        Transmission.AUTOMATIC,
                        FuelType.EV,
                        "Trắng",
                        60,
                        null
                );

        providerVehicleService.createVehicle(user.getUserId(), request1);

        CreateVehicleRequest duplicateRequest =
                new CreateVehicleRequest(
                        "CHASSIS2-" + uniqueSuffix,
                        "ENGINE2-" + uniqueSuffix,
                        request1.licensePlate(),
                        "VinFast",
                        "VF 8",
                        2024,
                        5,
                        Transmission.AUTOMATIC,
                        FuelType.EV,
                        "Đen",
                        60,
                        null
                );

        assertThatThrownBy(() -> providerVehicleService.createVehicle(user.getUserId(), duplicateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Biển số xe");
    }

    @Test
    @DisplayName("createVehicle throws IllegalArgumentException on invalid license plate pattern like 77AAAAAAA")
    void createVehicleRejectsInvalidLicensePlatePattern() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        CreateVehicleRequest request =
                new CreateVehicleRequest(
                        "CHASSIS-INVALID",
                        "ENGINE-INVALID",
                        "77AAAAAAA",
                        "VinFast",
                        "VF 8 Plus",
                        2024,
                        5,
                        Transmission.AUTOMATIC,
                        FuelType.EV,
                        "Xanh Lục Bảo",
                        60,
                        null
                );

        assertThatThrownBy(() -> providerVehicleService.createVehicle(user.getUserId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Biển số xe không đúng định dạng quy chuẩn Việt Nam");
    }

    @Test
    @DisplayName("getVehicleDetail returns vehicle detail for owner")
    void getVehicleDetailReturnsVehicleForOwner() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);

        var detail = providerVehicleService.getVehicleDetail(user.getUserId(), vehicle.getId());

        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo(vehicle.getId());
        assertThat(detail.licensePlate()).isEqualTo(vehicle.getLicensePlate());
        assertThat(detail.chassisNo()).isEqualTo(vehicle.getChassisNo());
        assertThat(detail.engineNo()).isEqualTo(vehicle.getEngineNo());
    }

    @Test
    @DisplayName("updateVehicle updates Tier 3 fields successfully without altering status")
    void updateVehicleUpdatesTier3WithoutChangingStatus() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);
        VehicleStatus originalStatus = vehicle.getStatus();

        UpdateVehicleRequest request =
                new UpdateVehicleRequest(
                        vehicle.getLicensePlate(),
                        "VinFast",
                        "VF 8 Plus New",
                        2025,
                        7,
                        Transmission.AUTOMATIC,
                        FuelType.EV,
                        "Trắng Ngọc Trai",
                        90,
                        "https://example.com/new-cover.jpg"
                );

        var updated = providerVehicleService.updateVehicle(user.getUserId(), vehicle.getId(), request);

        assertThat(updated).isNotNull();
        assertThat(updated.model()).isEqualTo("VF 8 Plus New");
        assertThat(updated.manufactureYear()).isEqualTo(2025);
        assertThat(updated.seatCount()).isEqualTo(7);
        assertThat(updated.color()).isEqualTo("Trắng Ngọc Trai");
        assertThat(updated.maintenanceBufferMinutes()).isEqualTo(90);
        assertThat(updated.status()).isEqualTo(originalStatus);
    }

    @Test
    @DisplayName("updateVehicle changing license plate triggers PENDING re-approval status")
    void updateVehicleChangingPlateTriggersPendingStatus() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);

        String randomSuffix = String.format("%02d", (int) (Math.random() * 90 + 10));
        String newPlate = "51K-999." + randomSuffix;

        UpdateVehicleRequest request =
                new UpdateVehicleRequest(
                        newPlate,
                        vehicle.getBrand(),
                        vehicle.getModel(),
                        vehicle.getManufactureYear(),
                        vehicle.getSeatCount(),
                        vehicle.getTransmission(),
                        vehicle.getFuelType(),
                        vehicle.getColor(),
                        vehicle.getMaintenanceBufferMinutes(),
                        null
                );

        var updated = providerVehicleService.updateVehicle(user.getUserId(), vehicle.getId(), request);

        assertThat(updated.licensePlate()).isEqualTo(newPlate);
        assertThat(updated.status()).isEqualTo(VehicleStatus.PENDING);
    }

    @Test
    @DisplayName("getVehicleRentalPlans returns standard 4 plans for owner")
    void getVehicleRentalPlansReturnsPlansForOwner() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);

        var plans = providerVehicleService.getVehicleRentalPlans(user.getUserId(), vehicle.getId());

        assertThat(plans).isNotNull();
        assertThat(plans).hasSize(4);
        assertThat(plans.stream().map(ProviderRentalPlanResponse::planType).toList())
                .contains(PlanType.HOURLY, PlanType.DAILY, PlanType.MONTHLY, PlanType.YEARLY);
    }

    @Test
    @DisplayName("updateVehicleRentalPlans updates prices and operational surcharges")
    void updateVehicleRentalPlansUpdatesPricesAndSurcharges() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);

        List<UpdateRentalPlansRequest.PlanItemRequest> planItems = List.of(
                new UpdateRentalPlansRequest.PlanItemRequest(
                        PlanType.HOURLY,
                        new BigDecimal("150000.00"),
                        PlanStatus.ACTIVE,
                        null,
                        480
                ),
                new UpdateRentalPlansRequest.PlanItemRequest(
                        PlanType.DAILY,
                        new BigDecimal("950000.00"),
                        PlanStatus.ACTIVE,
                        null,
                        null
                ),
                new UpdateRentalPlansRequest.PlanItemRequest(
                        PlanType.MONTHLY,
                        new BigDecimal("25000000.00"),
                        PlanStatus.ACTIVE,
                        null,
                        null
                ),
                new UpdateRentalPlansRequest.PlanItemRequest(
                        PlanType.YEARLY,
                        new BigDecimal("270000000.00"),
                        PlanStatus.ACTIVE,
                        new BigDecimal("60000000.00"),
                        null
                )
        );

        UpdateRentalPlansRequest request = new UpdateRentalPlansRequest(
                planItems,
                20,
                new BigDecimal("0.2000"),
                15,
                new BigDecimal("15000.00"),
                20,
                new BigDecimal("2500.00"),
                new BigDecimal("0.2000")
        );

        var updatedPlans = providerVehicleService.updateVehicleRentalPlans(user.getUserId(), vehicle.getId(), request);

        assertThat(updatedPlans).hasSize(4);

        var daily = updatedPlans.stream().filter(p -> p.planType() == PlanType.DAILY).findFirst().orElseThrow();
        assertThat(daily.slotPrice()).isEqualByComparingTo("950000.00");
        assertThat(daily.freeRadiusKm()).isEqualTo(15);
        assertThat(daily.outOfRadiusRate()).isEqualByComparingTo("15000.00");

        var yearly = updatedPlans.stream().filter(p -> p.planType() == PlanType.YEARLY).findFirst().orElseThrow();
        assertThat(yearly.slotPrice()).isEqualByComparingTo("270000000.00");
        assertThat(yearly.upfrontAmount()).isEqualByComparingTo("60000000.00");
        assertThat(yearly.status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(yearly.freeRadiusKm()).isNull();
        assertThat(yearly.allowedOvertimeMinutes()).isNull();
    }

    @Test
    @DisplayName("updateVehicleRentalPlans throws when active plan has no price")
    void updateVehicleRentalPlansThrowsWhenActivePlanHasNoPrice() {
        User user = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        Provider provider = userService.findProviderByUserId(user.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).get(0);

        List<UpdateRentalPlansRequest.PlanItemRequest> planItems = List.of(
                new UpdateRentalPlansRequest.PlanItemRequest(
                        PlanType.HOURLY,
                        null,
                        PlanStatus.ACTIVE,
                        null,
                        480
                )
        );

        UpdateRentalPlansRequest request = new UpdateRentalPlansRequest(
                planItems,
                15,
                new BigDecimal("0.1500"),
                10,
                new BigDecimal("12000.00"),
                15,
                new BigDecimal("2000.00"),
                new BigDecimal("0.1500")
        );

        assertThatThrownBy(() ->
                providerVehicleService.updateVehicleRentalPlans(user.getUserId(), vehicle.getId(), request)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
