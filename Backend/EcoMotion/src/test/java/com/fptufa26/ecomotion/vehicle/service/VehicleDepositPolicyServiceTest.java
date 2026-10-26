package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.service.implement.VehicleDepositPolicyServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDepositPolicyServiceTest {

    private final VehicleDepositPolicyService policy = new VehicleDepositPolicyServiceImpl();

    @Test
    void vehicleOverrideAndMinimumWinOverProviderDefault() {
        Provider provider = new Provider();
        provider.setDepositRatio(new BigDecimal("0.3000"));
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        vehicle.setDepositRatioOverride(new BigDecimal("0.4000"));
        vehicle.setMinimumDepositAmount(new BigDecimal("500000.00"));

        var result = policy.resolve(vehicle, new BigDecimal("1000000.00"));

        assertThat(result.ratio()).isEqualByComparingTo("0.4000");
        assertThat(result.amount()).isEqualByComparingTo("500000.00");
    }

    @Test
    void providerRatioIsUsedWhenVehicleHasNoOverride() {
        Provider provider = new Provider();
        provider.setDepositRatio(new BigDecimal("0.3000"));
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        vehicle.setMinimumDepositAmount(new BigDecimal("1000.00"));

        var result = policy.resolve(vehicle, new BigDecimal("1000000.00"));

        assertThat(result.ratio()).isEqualByComparingTo("0.3000");
        assertThat(result.amount()).isEqualByComparingTo("300000.00");
    }

    @Test
    void depositNeverExceedsRentalTotal() {
        Provider provider = new Provider();
        provider.setDepositRatio(new BigDecimal("0.3000"));
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        vehicle.setMinimumDepositAmount(new BigDecimal("5000000.00"));

        var result = policy.resolve(vehicle, new BigDecimal("1400000.00"));

        assertThat(result.amount()).isEqualByComparingTo("1400000.00");
    }

    @Test
    void missingMinimumDefaultsToZero() {
        Provider provider = new Provider();
        provider.setDepositRatio(new BigDecimal("0.1000"));
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);

        var result = policy.resolve(vehicle, new BigDecimal("5000.00"));

        assertThat(result.amount()).isEqualByComparingTo("500.00");
    }
}
