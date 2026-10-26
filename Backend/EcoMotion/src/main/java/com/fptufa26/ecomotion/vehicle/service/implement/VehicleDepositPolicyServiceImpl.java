package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.service.VehicleDepositPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class VehicleDepositPolicyServiceImpl implements VehicleDepositPolicyService {

    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.3000");

    @Override
    public DepositQuote resolve(Vehicle vehicle, BigDecimal rentalTotal) {
        BigDecimal total = rentalTotal == null ? BigDecimal.ZERO : rentalTotal.max(BigDecimal.ZERO);
        BigDecimal ratio = vehicle.getDepositRatioOverride();
        if (ratio == null && vehicle.getProvider() != null) {
            ratio = vehicle.getProvider().getDepositRatio();
        }
        if (ratio == null) {
            ratio = DEFAULT_RATIO;
        }
        BigDecimal minimum = vehicle.getMinimumDepositAmount() == null
                ? BigDecimal.ZERO
                : vehicle.getMinimumDepositAmount().max(BigDecimal.ZERO);
        BigDecimal calculated = total.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = calculated.max(minimum).min(total).setScale(2, RoundingMode.HALF_UP);
        return new DepositQuote(ratio, minimum.setScale(2, RoundingMode.HALF_UP), amount);
    }
}
