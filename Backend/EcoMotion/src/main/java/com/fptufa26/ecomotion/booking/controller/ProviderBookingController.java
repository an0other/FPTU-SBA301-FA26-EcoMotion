package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.AssignDriverRequest;
import com.fptufa26.ecomotion.booking.dto.request.DenyBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest;
import com.fptufa26.ecomotion.booking.dto.response.OperationalResolutionResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.SettlementResultResponse;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/provider/bookings", "/api/v1/provider/bookings", "/api/v1/provider/trips"})
@RequiredArgsConstructor
@Tag(name = "Provider Bookings", description = "Provider booking queue and management (O07, O08)")
public class ProviderBookingController {

    private final ProviderBookingService providerBookingService;
    private final VehicleOperationalRiskService vehicleOperationalRiskService;

    @GetMapping
    @Operation(summary = "List the current provider's bookings")
    public ApiResponse<List<ProviderBookingResponse>> listBookings(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(providerBookingService.listBookings(principal.getUserId(), status));
    }

    @GetMapping("/paged")
    @Operation(summary = "List the current provider's bookings with pagination")
    public ApiResponse<PageResponse<ProviderBookingResponse>> listBookingsPaged(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(providerBookingService.listBookingsPaged(
                principal.getUserId(), status, Math.max(0, offset), Math.max(1, limit)));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get booking summary counters by status")
    public ApiResponse<ProviderBookingSummaryResponse> getSummary(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerBookingService.getSummary(principal.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full booking detail by code or ID")
    public ApiResponse<ProviderBookingDetailResponse> getBookingDetail(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getBookingDetail(principal.getUserId(), id));
    }

    @GetMapping("/{id}/operational-risk")
    @Operation(summary = "Kiểm tra nguy cơ xe chưa về kịp cho booking kế tiếp")
    public ApiResponse<com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse> operationalRisk(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(vehicleOperationalRiskService.assessForProvider(principal.getUserId(), id));
    }

    @PostMapping("/{id}/operational-resolution")
    @Operation(summary = "Đổi xe, dời lịch hoặc hủy hoàn tiền khi xe có nguy cơ về trễ")
    public ApiResponse<OperationalResolutionResponse> resolveOperationalRisk(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @RequestBody @Valid ResolveOperationalRiskRequest request) {
        return ApiResponse.success(providerBookingService.resolveOperationalRisk(
                principal.getUserId(), id, request));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a pending booking")
    public ApiResponse<ProviderBookingResponse> acceptBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.acceptBooking(principal.getUserId(), id));
    }

    @PostMapping("/{id}/deny")
    @Operation(summary = "Deny a pending booking with reason")
    public ApiResponse<ProviderBookingResponse> denyBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @RequestBody(required = false) @Valid DenyBookingRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.success(providerBookingService.denyBooking(principal.getUserId(), id, reason));
    }

    @PutMapping("/{id}/driver")
    @Operation(summary = "Assign or reassign a driver to a booking")
    public ApiResponse<ProviderBookingResponse> assignDriver(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @RequestBody @Valid AssignDriverRequest request) {
        return ApiResponse.success(providerBookingService.assignDriver(
                principal.getUserId(), id, request.driverId(), request.mode()));
    }

    @GetMapping("/{id}/charges")
    @Operation(summary = "Get charges breakdown for a booking")
    public ApiResponse<List<BookingChargeResponse>> getCharges(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getCharges(principal.getUserId(), id));
    }

    @PostMapping("/{id}/charges")
    @Operation(summary = "Add a manual charge to a booking")
    public ApiResponse<BookingChargeResponse> addCharge(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @RequestBody @Valid AddChargeRequest request) {
        return ApiResponse.success(providerBookingService.addCharge(principal.getUserId(), id, request));
    }

    @PostMapping("/{id}/settlement/finalize")
    @Operation(summary = "Lock charges and create the final VietQR payment request")
    public ApiResponse<SettlementResultResponse> finalizeSettlement(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.finalizeSettlement(principal.getUserId(), id));
    }

    @GetMapping("/{id}/tracking")
    @Operation(summary = "Get live trip tracking and vehicle location for provider")
    public ApiResponse<com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse> getTripTracking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getTripTracking(principal.getUserId(), id));
    }

    @GetMapping("/{id}/location")
    @Operation(summary = "Get latest reported location of the vehicle for provider")
    public ApiResponse<com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse> getTripLocation(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getTripLocation(principal.getUserId(), id));
    }

    @GetMapping("/export")
    @Operation(summary = "Export bookings to CSV format")
    public ResponseEntity<byte[]> exportBookings(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status) {
        byte[] csvData = providerBookingService.exportBookingsCsv(principal.getUserId(), status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookings-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
