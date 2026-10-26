package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateRentalPlansRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDocumentRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDepositPolicyRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderRentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDocumentResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDepositPolicyResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import com.fptufa26.ecomotion.vehicle.service.LicensePlateHistoryService;
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
    private final com.fptufa26.ecomotion.common.storage.FileStorageService fileStorageService;
    private final LicensePlateHistoryService licensePlateHistoryService;

    public ProviderVehicleController(ProviderVehicleService providerVehicleService,
                                     com.fptufa26.ecomotion.common.storage.FileStorageService fileStorageService,
                                     LicensePlateHistoryService licensePlateHistoryService) {
        this.providerVehicleService = providerVehicleService;
        this.fileStorageService = fileStorageService;
        this.licensePlateHistoryService = licensePlateHistoryService;
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

    @GetMapping("/vehicles/{id}/license-plate-history")
    @Operation(summary = "Xem lịch sử thay đổi biển số xe")
    public ApiResponse<List<com.fptufa26.ecomotion.vehicle.dto.response.VehicleLicensePlateHistoryResponse>>
    getLicensePlateHistory(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return ApiResponse.success(licensePlateHistoryService.listForProvider(user.getUserId(), id));
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

    @PatchMapping("/vehicles/{id}/deposit-policy")
    @Operation(summary = "Cập nhật chính sách đặt cọc của xe")
    public ApiResponse<VehicleDepositPolicyResponse> updateDepositPolicy(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleDepositPolicyRequest request
    ) {
        return ApiResponse.success(providerVehicleService.updateDepositPolicy(user.getUserId(), id, request));
    }

    @GetMapping("/vehicles/{id}/deposit-policy")
    @Operation(summary = "Xem chính sách đặt cọc của xe")
    public ApiResponse<VehicleDepositPolicyResponse> getDepositPolicy(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerVehicleService.getDepositPolicy(user.getUserId(), id));
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

    @GetMapping("/vehicles/{id}/documents")
    @Operation(summary = "Get provider vehicle legal documents (O04)")
    public ApiResponse<List<ProviderVehicleDocumentResponse>> getVehicleDocuments(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerVehicleService.getVehicleDocuments(user.getUserId(), id));
    }

    @PutMapping("/vehicles/{id}/documents")
    @Operation(summary = "Upload or renew provider vehicle legal document (O04)")
    public ApiResponse<ProviderVehicleDocumentResponse> updateVehicleDocument(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateVehicleDocumentRequest request
    ) {
        return ApiResponse.success(providerVehicleService.updateVehicleDocument(user.getUserId(), id, request));
    }

    @PostMapping(value = "/vehicles/documents/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload real vehicle legal document file (JPG, PNG, PDF)")
    public ApiResponse<java.util.Map<String, String>> uploadDocumentFile(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn tệp tài liệu hoặc hình ảnh");
        }
        String contentType = file.getContentType();
        boolean isImage = contentType != null && (contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/png"));
        boolean isPdf = contentType != null && contentType.equalsIgnoreCase("application/pdf");
        if (!isImage && !isPdf) {
            throw new IllegalArgumentException("Hệ thống chỉ chấp nhận tệp hình ảnh (JPG, PNG) hoặc tài liệu PDF");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String storedKey = fileStorageService.store(
                    "vehicle",
                    user.getUserId(),
                    originalFilename,
                    file.getBytes(),
                    contentType
            );
            String fileUrl = "/api/files/" + storedKey;
            return ApiResponse.success(java.util.Map.of(
                    "fileUrl", fileUrl,
                    "fileName", originalFilename != null ? originalFilename : "document"
            ));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không thể đọc tệp tải lên: " + e.getMessage());
        }
    }
}
