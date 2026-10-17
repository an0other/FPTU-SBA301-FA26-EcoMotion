package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
@Tag(name = "Provider Vehicle Management", description = "Provider fleet management and controls (O01, O02)")
public class ProviderVehicleController {

    private final ProviderVehicleService providerVehicleService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get provider overview summary numbers (O01)")
    public ApiResponse<ProviderDashboardSummaryResponse> getDashboardSummary(
            @AuthenticationPrincipal SecurityUser user
    ) {
        return ApiResponse.success(providerVehicleService.getDashboardSummary(user.getUserId()));
    }

    @GetMapping("/vehicles")
    @Operation(summary = "Get provider vehicle fleet with stats and filters (O02)")
    public ApiResponse<ProviderVehiclePageResponse> getProviderVehicles(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.success(providerVehicleService.getProviderVehicles(
                user.getUserId(),
                keyword,
                fuelType,
                status,
                sortBy,
                page,
                size
        ));
    }

    @PatchMapping("/vehicles/{id}/visibility")
    @Operation(summary = "Toggle vehicle visibility (O02)")
    public ApiResponse<ProviderVehicleItemResponse> toggleVehicleVisibility(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody VehicleVisibilityRequest request
    ) {
        return ApiResponse.success(providerVehicleService.toggleVehicleVisibility(
                user.getUserId(),
                id,
                request
        ));
    }
}
