package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.service.RenterDrivingLicenseService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/renter/driving-license")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RENTER')")
@Tag(name = "Renter Driving License")
public class RenterDrivingLicenseController {

    private final RenterDrivingLicenseService drivingLicenseService;

    @GetMapping
    @Operation(summary = "Get the authenticated renter's driving license")
    public ApiResponse<DrivingLicenseResponse> getDrivingLicense(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(
                drivingLicenseService.getDrivingLicense(
                        principal.getUserId()));
    }

    @PutMapping
    @Operation(summary = "Declare or update the authenticated renter's driving license")
    public ApiResponse<DrivingLicenseResponse> updateDrivingLicense(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody UpdateDrivingLicenseRequest request) {
        return ApiResponse.success(
                drivingLicenseService.updateDrivingLicense(
                        principal.getUserId(),
                        request));
    }
}