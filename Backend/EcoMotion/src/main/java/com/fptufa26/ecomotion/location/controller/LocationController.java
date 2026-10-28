package com.fptufa26.ecomotion.location.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.location.dto.response.AddressSearchResult;
import com.fptufa26.ecomotion.location.dto.response.ReverseGeocodeResponse;
import com.fptufa26.ecomotion.location.service.MapLocationService;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
@Tag(name = "Location", description = "Map search and address lookup")
public class LocationController {

    private final MapLocationService mapLocationService;
    private final VehicleService vehicleService;

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

}
