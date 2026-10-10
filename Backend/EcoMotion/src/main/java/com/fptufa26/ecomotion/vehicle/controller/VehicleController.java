package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Rentable vehicle fleet")
public class VehicleController {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 100;

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "List rentable vehicles",
            description = "One offset/limit page of approved vehicles with cover image and active "
                    + "plans. All filters optional. `sortBy` orders the whole matching set before "
                    + "the page is cut, so paging stays consistent with the chosen order. "
                    + "Responds with items + total/offset/limit/hasMore.")
    public ApiResponse<PageResponse<VehicleCardResponse>> listVehicles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.success(vehicleService.listRentableVehicles(
                keyword, location, fuelType, planType, maxPrice, VehicleSort.from(sortBy),
                Math.max(0, offset), clampLimit(limit)));
    }

    private int clampLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
