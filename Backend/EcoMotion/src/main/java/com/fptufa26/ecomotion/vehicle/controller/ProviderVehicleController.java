package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateRentalPlansRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderRentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/provider")
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
@Tag(name = "Provider Vehicle Management", description = "Provider fleet management and controls (O01, O02)")
public class ProviderVehicleController {

    private final ProviderVehicleService providerVehicleService;

    public ProviderVehicleController(ProviderVehicleService providerVehicleService) {
        this.providerVehicleService = providerVehicleService;
    }

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

    @PostMapping("/vehicles")
    @Operation(summary = "Register a new vehicle into fleet (O03)")
    public ApiResponse<ProviderVehicleDetailResponse> createVehicle(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        return ApiResponse.success(providerVehicleService.createVehicle(user.getUserId(), request));
    }

    @GetMapping("/vehicles/{id}")
    @Operation(summary = "Get provider vehicle detail for hub & edit (O04)")
    public ApiResponse<ProviderVehicleDetailResponse> getVehicleDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerVehicleService.getVehicleDetail(user.getUserId(), id));
    }

    @PutMapping("/vehicles/{id}")
    @Operation(summary = "Update provider vehicle profile (O04)")
    public ApiResponse<ProviderVehicleDetailResponse> updateVehicle(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return ApiResponse.success(providerVehicleService.updateVehicle(user.getUserId(), id, request));
    }

    @GetMapping("/vehicles/{id}/plans")
    @Operation(summary = "Get provider vehicle rental plans (O04)")
    public ApiResponse<List<ProviderRentalPlanResponse>> getVehicleRentalPlans(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerVehicleService.getVehicleRentalPlans(user.getUserId(), id));
    }

    @PutMapping("/vehicles/{id}/plans")
    @Operation(summary = "Update provider vehicle rental plans (O04)")
    public ApiResponse<List<ProviderRentalPlanResponse>> updateVehicleRentalPlans(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRentalPlansRequest request
    ) {
        return ApiResponse.success(providerVehicleService.updateVehicleRentalPlans(user.getUserId(), id, request));
    }
}
