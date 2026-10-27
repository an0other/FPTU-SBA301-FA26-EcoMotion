package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverActivityStatusRequest;
import com.fptufa26.ecomotion.user.dto.response.DriverStatusResponse;
import com.fptufa26.ecomotion.user.service.DriverStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/driver/status", "/api/driver/status"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Status", description = "Driver status management and toggle (online/offline/unavailable)")
@SecurityRequirement(name = "bearerAuth")
public class DriverStatusController {

    private final DriverStatusService driverStatusService;

    @GetMapping
    @Operation(summary = "Get current driver operational status and trip overview")
    public ApiResponse<DriverStatusResponse> getStatus(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(driverStatusService.getDriverStatus(principal.getUserId()));
    }

    @PutMapping
    @Operation(summary = "Driver updates readiness / activity status (AVAILABLE, UNAVAILABLE, OFFLINE)")
    public ApiResponse<DriverStatusResponse> updateStatus(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody UpdateDriverActivityStatusRequest request) {
        return ApiResponse.success(driverStatusService.updateActivityStatus(principal.getUserId(), request.activityStatus()));
    }
}
