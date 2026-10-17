package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
@Tag(name = "Provider Dashboard", description = "Provider overview and quick counters (O01)")
public class ProviderVehicleController {

    private final ProviderVehicleService providerVehicleService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get provider overview summary numbers (O01)")
    public ApiResponse<ProviderDashboardSummaryResponse> getDashboardSummary(
            @AuthenticationPrincipal SecurityUser user
    ) {
        return ApiResponse.success(providerVehicleService.getDashboardSummary(user.getUserId()));
    }
}
