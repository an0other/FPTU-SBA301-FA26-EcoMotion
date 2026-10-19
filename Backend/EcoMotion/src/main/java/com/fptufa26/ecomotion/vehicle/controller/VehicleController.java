package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleAvailabilityResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ReverseGeocodeResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.AddressSearchResult;
import com.fptufa26.ecomotion.vehicle.service.MapLocationService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Rentable vehicle fleet")
public class VehicleController {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 100;

    private final VehicleService vehicleService;
    private final MapLocationService mapLocationService;

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

    @GetMapping("/map")
    @Operation(summary = "Find rentable vehicles near a selected position")
    public ApiResponse<List<VehicleMapResponse>> findNearbyVehicles(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam int radiusKm,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String planType,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy) {
        return ApiResponse.success(vehicleService.findNearbyVehicles(latitude, longitude, radiusKm,
                keyword, location, fuelType, planType, maxPrice, VehicleSort.from(sortBy)));
    }

    @GetMapping("/map/reverse")
    @Operation(summary = "Get an approximate address for a selected map point")
    public ApiResponse<ReverseGeocodeResponse> reverseGeocode(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude) {
        return ApiResponse.success(new ReverseGeocodeResponse(
                mapLocationService.reverseGeocode(latitude, longitude)));
    }

    @GetMapping("/map/search")
    @Operation(summary = "Find map positions by a submitted address")
    public ApiResponse<List<AddressSearchResult>> searchAddress(@RequestParam String query) {
        return ApiResponse.success(mapLocationService.searchAddress(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a rentable vehicle's detail",
            description = "Public detail of a single APPROVED vehicle with its images in display "
                    + "order and its ACTIVE plans. Responds 404 when the vehicle does not exist or "
                    + "is not approved. Never exposes the license plate, chassis number, engine "
                    + "number or vehicle documents.")
    public ApiResponse<VehicleDetailResponse> getVehicle(@PathVariable Long id) {
        return ApiResponse.success(vehicleService.getVehicleDetail(id));
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Get occupied time slots for a vehicle",
            description = "Returns all active schedule entries (booked, maintenance, blocked) "
                    + "that overlap the requested time range. Use this to grey-out unavailable "
                    + "dates/times in the booking wizard.")
    public ApiResponse<VehicleAvailabilityResponse> getAvailability(
            @PathVariable Long id,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ApiResponse.success(vehicleService.getAvailability(id, from, to));
    }
}
