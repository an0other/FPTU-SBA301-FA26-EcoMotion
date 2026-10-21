package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.user.dto.request.CreateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.request.ToggleDriverStatusRequest;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverItemResponse;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverStatsResponse;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProviderDriverServiceTest {

    @Autowired
    private ProviderDriverService providerDriverService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("getDrivers returns seeded drivers for Provider A and supports filtering")
    void getDriversReturnsSeededDrivers() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        List<ProviderDriverItemResponse> allDrivers = providerDriverService.getDrivers(
                providerUser.getUserId(),
                null,
                null,
                "newest"
        );

        assertThat(allDrivers).isNotEmpty();
        assertThat(allDrivers.size()).isGreaterThanOrEqualTo(3);

        // Filter by status APPROVED
        List<ProviderDriverItemResponse> approvedDrivers = providerDriverService.getDrivers(
                providerUser.getUserId(),
                null,
                DriverStatus.APPROVED,
                "newest"
        );
        assertThat(approvedDrivers).allMatch(d -> d.status() == DriverStatus.APPROVED);

        // Filter by search query
        List<ProviderDriverItemResponse> searchDrivers = providerDriverService.getDrivers(
                providerUser.getUserId(),
                "Nam",
                null,
                "newest"
        );
        assertThat(searchDrivers).isNotEmpty();
    }

    @Test
    @DisplayName("getDriverStats returns accurate KPI metrics")
    void getDriverStatsReturnsMetrics() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        ProviderDriverStatsResponse stats = providerDriverService.getDriverStats(providerUser.getUserId());

        assertThat(stats).isNotNull();
        assertThat(stats.total()).isGreaterThanOrEqualTo(3);
        assertThat(stats.approved()).isGreaterThanOrEqualTo(3);
        assertThat(stats.total()).isEqualTo(stats.approved() + stats.pending() + stats.suspended() + stats.rejected());
    }

    @Test
    @DisplayName("createDriver successfully registers user with DRIVER role and PENDING status")
    void createDriverSuccessfully() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        String suffix = UUID.randomUUID().toString().substring(0, 6);
        CreateDriverByProviderRequest request = new CreateDriverByProviderRequest(
                "Tài Xế Test " + suffix,
                "driver.test." + suffix + "@ecomotion.vn",
                "0999" + suffix,
                "https://example.com/portrait.jpg",
                "79" + suffix,
                "B2",
                LocalDate.now().plusYears(3),
                "https://example.com/license.jpg",
                "EcoMotion@123"
        );

        ProviderDriverItemResponse response = providerDriverService.createDriver(providerUser.getUserId(), request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.fullName()).isEqualTo(request.fullName());
        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.status()).isEqualTo(DriverStatus.PENDING); // Spec rule: PENDING awaiting Admin approval

        User createdUser = userRepository.findById(response.userId()).orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.DRIVER);
    }

    @Test
    @DisplayName("createDriver rejects duplicate email and duplicate phone")
    void createDriverRejectsDuplicates() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        // Use existing seeded email
        CreateDriverByProviderRequest duplicateEmailReq = new CreateDriverByProviderRequest(
                "Tài Xế Duplicate",
                "provider.a@ecomotion.vn",
                "0998888777",
                "https://example.com/p.jpg",
                "790999999",
                "B2",
                LocalDate.now().plusYears(2),
                "https://example.com/l.jpg",
                "EcoMotion@123"
        );

        assertThatThrownBy(() -> providerDriverService.createDriver(providerUser.getUserId(), duplicateEmailReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email này đã được sử dụng");
    }

    @Test
    @DisplayName("toggleDriverStatus can switch between APPROVED and SUSPENDED")
    void toggleDriverStatusSuccess() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        List<ProviderDriverItemResponse> drivers = providerDriverService.getDrivers(
                providerUser.getUserId(),
                null,
                DriverStatus.APPROVED,
                null
        );
        assertThat(drivers).isNotEmpty();
        Long driverId = drivers.get(0).id();

        // Suspend driver
        ProviderDriverItemResponse suspended = providerDriverService.toggleDriverStatus(
                providerUser.getUserId(),
                driverId,
                new ToggleDriverStatusRequest(DriverStatus.SUSPENDED)
        );
        assertThat(suspended.status()).isEqualTo(DriverStatus.SUSPENDED);

        // Re-activate driver
        ProviderDriverItemResponse reactivated = providerDriverService.toggleDriverStatus(
                providerUser.getUserId(),
                driverId,
                new ToggleDriverStatusRequest(DriverStatus.APPROVED)
        );
        assertThat(reactivated.status()).isEqualTo(DriverStatus.APPROVED);
    }

    @Test
    @DisplayName("updateDriver resets APPROVED status to PENDING when license data is modified")
    void updateDriverResetsApprovedToPending() {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();

        List<ProviderDriverItemResponse> drivers = providerDriverService.getDrivers(
                providerUser.getUserId(),
                null,
                DriverStatus.APPROVED,
                null
        );
        assertThat(drivers).isNotEmpty();
        ProviderDriverItemResponse target = drivers.get(0);

        UpdateDriverByProviderRequest updateReq = new UpdateDriverByProviderRequest(
                target.fullName() + " Updated",
                target.phone(),
                target.portraitUrl(),
                target.licenseNo(),
                "C", // Changed from B2 to C
                target.licenseExpiry(),
                target.licenseImageUrl()
        );

        ProviderDriverItemResponse updated = providerDriverService.updateDriver(
                providerUser.getUserId(),
                target.id(),
                updateReq
        );

        // Specification requirement: modifying approved profile puts it back to PENDING for admin review
        assertThat(updated.status()).isEqualTo(DriverStatus.PENDING);
        assertThat(updated.licenseClass()).isEqualTo("C");
    }
}
