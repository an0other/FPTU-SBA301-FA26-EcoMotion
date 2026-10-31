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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.fptufa26.ecomotion.booking.dto.request.ResolveIncidentRequest;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripIncidentResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.service.TripEvidenceService;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/provider/bookings", "/api/v1/provider/bookings", "/api/v1/provider/trips"})
@RequiredArgsConstructor
@Tag(name = "Provider Bookings", description = "Provider booking queue and management (O07, O08)")
public class ProviderBookingController {

    private final ProviderBookingService providerBookingService;
    private final VehicleOperationalRiskService vehicleOperationalRiskService;
    private final TripEvidenceService tripEvidenceService;

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
    public ApiResponse<OperationalRiskResponse> operationalRisk(
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
    public ApiResponse<TripTrackingResponse> getTripTracking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getTripTracking(principal.getUserId(), id));
    }

    @GetMapping("/{id}/location")
    @Operation(summary = "Get latest reported location of the vehicle for provider")
    public ApiResponse<TripLocationResponse> getTripLocation(
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

    @GetMapping("/{id}/assign-candidates")
    @Operation(summary = "Get list of eligible driver candidates with suitability scores and ETA")
    public ApiResponse<List<DriverCandidateResponse>> getAssignmentCandidates(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getAssignmentCandidates(principal.getUserId(), id));
    }

    @PostMapping("/{id}/assign-suggest")
    @Operation(summary = "Run Google OR-Tools CP-SAT solver and suggest optimal driver for booking")
    public ApiResponse<AssignmentSuggestionResponse> suggestDriverAssignment(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.suggestDriverAssignment(principal.getUserId(), id));
    }

    @PostMapping("/{id}/auto-assign")
    @Operation(summary = "Run Google OR-Tools CP-SAT and automatically assign optimal driver")
    public ApiResponse<ProviderBookingResponse> autoAssignDriver(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.autoAssignDriver(principal.getUserId(), id));
    }

    @GetMapping("/{id}/incidents")
    @Operation(summary = "Get list of reported incidents for a booking")
    public ApiResponse<List<TripIncidentResponse>> getBookingIncidents(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(providerBookingService.getBookingIncidents(principal.getUserId(), id));
    }

    @PostMapping("/{id}/incidents/{incidentId}/resolve")
    @Operation(summary = "Provider resolves a reported trip incident")
    public ApiResponse<String> resolveIncident(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @PathVariable("incidentId") Long incidentId,
            @Valid @RequestBody ResolveIncidentRequest request) {
        providerBookingService.resolveIncident(principal.getUserId(), id, incidentId, request);
        return ApiResponse.success("Đã cập nhật trạng thái sự cố thành công");
    }

    @PostMapping(value = "/{id}/evidence/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Provider uploads trip evidence photo to Cloudinary")
    public ApiResponse<TripEvidenceResponse> uploadEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") TripEvidenceType type,
            @RequestParam(value = "note", required = false) String note) {
        return ApiResponse.success(tripEvidenceService.uploadEvidence(
                principal.getUserId(),
                principal.getRole(),
                id,
                file,
                type,
                note
        ));
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    @Operation(summary = "Provider deletes trip evidence photo")
    public ApiResponse<String> deleteEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id,
            @PathVariable("evidenceId") Long evidenceId) {
        tripEvidenceService.deleteEvidence(principal.getUserId(), principal.getRole(), id, evidenceId);
        return ApiResponse.success("Đã xóa ảnh minh chứng thành công");
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "Get trip handover, pickup, and completion evidence photos")
    public ApiResponse<List<TripEvidenceResponse>> getBookingEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("id") String id) {
        return ApiResponse.success(tripEvidenceService.listEvidence(principal.getUserId(), principal.getRole(), id));
    }
}
