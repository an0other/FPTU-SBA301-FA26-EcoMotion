package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseClassOptionResponse;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.service.RenterDrivingLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    @GetMapping("/classes")
    @Operation(summary = "List driving-license classes supported by yearly rental policy")
    public ApiResponse<List<DrivingLicenseClassOptionResponse>> getSupportedClasses() {
        return ApiResponse.success(drivingLicenseService.getSupportedLicenseClasses());
    }

    @PostMapping("/upload")
    @Operation(summary = "Read and privately store both sides of the renter's driving license")
    public ApiResponse<DrivingLicenseResponse> uploadDrivingLicense(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam("front") MultipartFile front,
            @RequestParam("back") MultipartFile back) {
        return ApiResponse.success(drivingLicenseService.uploadDrivingLicense(
                principal.getUserId(), front, back));
    }

    @GetMapping("/images/{side}")
    @Operation(summary = "View one privately stored side of the authenticated renter's driving license")
    public ResponseEntity<byte[]> getDrivingLicenseImage(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String side) {
        return drivingLicenseService.getDrivingLicenseImage(principal.getUserId(), side)
                .map(image -> ResponseEntity.ok()
                        .contentType(image.mediaType())
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(image.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
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