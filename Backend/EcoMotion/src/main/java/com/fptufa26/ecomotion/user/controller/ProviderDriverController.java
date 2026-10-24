package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.booking.dto.response.AvailableDriverResponse;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.user.dto.request.CreateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.request.ToggleDriverStatusRequest;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverItemResponse;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverStatsResponse;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.service.ProviderDriverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/provider/drivers")
@RequiredArgsConstructor
@Tag(name = "Provider Drivers", description = "Provider driver management")
public class ProviderDriverController {

    private final ProviderBookingService providerBookingService;
    private final ProviderDriverService providerDriverService;
    private final FileStorageService fileStorageService;

    @GetMapping(value = "/available", params = "!bookingId")
    @Operation(summary = "Get available drivers for booking assignment")
    public ApiResponse<List<AvailableDriverResponse>> getAvailableDrivers(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerBookingService.getAvailableDrivers(principal.getUserId()));
    }

    @GetMapping
    @Operation(summary = "Get list of provider drivers with optional filter and sort")
    public ApiResponse<List<ProviderDriverItemResponse>> getDrivers(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.success(providerDriverService.getDrivers(principal.getUserId(), search, status, sort));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get driver metrics for KPI cards")
    public ApiResponse<ProviderDriverStatsResponse> getStats(
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return ApiResponse.success(providerDriverService.getDriverStats(principal.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver details by ID")
    public ApiResponse<ProviderDriverItemResponse> getDriverById(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerDriverService.getDriverById(principal.getUserId(), id));
    }

    @PostMapping
    @Operation(summary = "Proxy create new driver profile and account")
    public ApiResponse<ProviderDriverItemResponse> createDriver(
            @AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody CreateDriverByProviderRequest request
    ) {
        return ApiResponse.success(providerDriverService.createDriver(principal.getUserId(), request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update driver profile and license info")
    public ApiResponse<ProviderDriverItemResponse> updateDriver(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDriverByProviderRequest request
    ) {
        return ApiResponse.success(providerDriverService.updateDriver(principal.getUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle driver status between APPROVED and SUSPENDED")
    public ApiResponse<ProviderDriverItemResponse> toggleStatus(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long id,
            @Valid @RequestBody ToggleDriverStatusRequest request
    ) {
        return ApiResponse.success(providerDriverService.toggleDriverStatus(principal.getUserId(), id, request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload driver portrait or license photo")
    public ApiResponse<Map<String, String>> uploadDriverImage(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn tệp hình ảnh");
        }
        String contentType = file.getContentType();
        boolean isImage = contentType != null && (
                contentType.equalsIgnoreCase("image/jpeg") ||
                contentType.equalsIgnoreCase("image/png") ||
                contentType.equalsIgnoreCase("image/webp")
        );
        if (!isImage) {
            throw new IllegalArgumentException("Hệ thống chỉ chấp nhận hình ảnh định dạng JPG, PNG hoặc WEBP");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String safeName = (originalFilename != null && !originalFilename.isBlank())
                    ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "driver_image.png";
            String uniqueFilename = System.currentTimeMillis() + "_" + safeName;
            String storedKey = fileStorageService.store(
                    "driver",
                    principal.getUserId(),
                    uniqueFilename,
                    file.getBytes(),
                    contentType
            );
            String downloadUrl = "/api/files/" + storedKey;
            return ApiResponse.success(Map.of("url", downloadUrl, "key", storedKey));
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi đọc file ảnh: " + e.getMessage(), e);
        }
    }
}
