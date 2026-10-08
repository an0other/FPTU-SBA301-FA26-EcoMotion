package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;

/**
 * Public API of the {@code vehicle} module for reading the rentable fleet. Declared as an
 * interface because it is a deliberate module boundary — other modules resolve a vehicle through
 * this service instead of touching {@code VehicleRepository} directly (§8.2 of AGENT_CODE_STRUCTURE).
 */
public interface VehicleService {

    /**
     * One offset/limit page of rentable vehicles (status = APPROVED), each with its cover image and
     * active plans.
     *
     * @param keyword  optional, matched against brand/model/license plate/provider (case-insensitive)
     * @param location optional, matched against the provider's depot address (case-insensitive)
     * @param fuelType optional fuel-type filter
     * @param planType optional filter: only vehicles offering this rental plan
     * @param maxPrice optional ceiling, read on the scale of {@code planType} — or of the cheapest
     *                 plan when no plan filter is set
     * @param offset   zero-based index of the first row to return (clamped to {@code >= 0})
     * @param limit    page size; clamped to a sane range by the caller
     */
    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           java.math.BigDecimal maxPrice,
                                                           int offset, int limit);
}
