package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.entity.FuelType;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Transmission;

import java.math.BigDecimal;
import java.util.List;

/**
 * One row of the renter-facing vehicle list (the "vehicle card" of the design system).
 *
 * <p>Shape mirrors the card layout: visual zone (cover image + trust tags), identity zone
 * (name, transmission/fuel), technical zone (seats, plate, rating) and the commercial footer
 * (price per plan + provider). Prices are the CURRENT {@code rental_plan} figures — fine for
 * browsing; any real booking later re-reads the plan to build the policy snapshot.
 */
public record VehicleCardResponse(
        Long id,
        String brand,
        String model,
        Integer manufactureYear,
        String licensePlate,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String coverImageUrl,
        List<String> imageUrls,
        Long providerId,
        String providerName,
        String providerHotline,
        String providerDepotAddress,
        List<PlanPrice> plans
) {

    /** Price of one rental plan, e.g. HOURLY 200.000 đ / 60 phút. */
    public record PlanPrice(
            PlanType planType,
            Integer slotLengthMinutes,
            BigDecimal slotPrice,
            BigDecimal upfrontAmount
    ) {
    }
}
