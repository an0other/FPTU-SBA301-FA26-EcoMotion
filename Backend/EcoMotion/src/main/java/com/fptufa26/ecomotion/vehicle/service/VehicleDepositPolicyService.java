package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.math.BigDecimal;

public interface VehicleDepositPolicyService {

    DepositQuote resolve(Vehicle vehicle, BigDecimal rentalTotal);

    record DepositQuote(BigDecimal ratio, BigDecimal minimumAmount, BigDecimal amount) {
    }
}
