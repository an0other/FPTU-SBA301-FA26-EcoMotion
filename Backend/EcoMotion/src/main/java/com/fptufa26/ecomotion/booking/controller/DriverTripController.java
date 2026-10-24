package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.service.DriverTripService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/driver/trips", "/api/driver/trips", "/api/driver/schedule"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Schedule & Trip Execution", description = "Driver trip lifecycle, execution, and location tracking (D01, D02)")
@SecurityRequirement(name = "bearerAuth")
public class DriverTripController {

    private final DriverTripService driverTripService;
    private final com.fptufa26.ecomotion.booking.service.TripEvidenceService tripEvidenceService;

    @GetMapping
    @Operation(
            summary = "List assigned trips for current driver",
            description = "Retrieve paginated list of assigned trips for the authenticated driver."
    )
    public ApiResponse<PageResponse<DriverTripResponse>> listTrips(
            @AuthenticationPrincipal SecurityUser principal,
            @Parameter(description = "Schedule view filter: TODAY, UPCOMING, HISTORY, ACTIVE", example = "TODAY")
            @RequestParam(required = false) String view,
            @Parameter(description = "Booking status filter: PENDING, ACCEPTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, or ASSIGNED", example = "CONFIRMED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by specific scheduled pickup date (Vietnam calendar date, YYYY-MM-DD)", example = "2026-10-17")
            @RequestParam(required = false) String date,
            @Parameter(description = "Page number (0-indexed, default 0)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (1-100, default 20)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Offset for pagination fallback", example = "0")
            @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Limit for pagination fallback", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Sorting criteria: property[,asc|desc]", example = "pickupTime,asc")
            @RequestParam(required = false) String sort) {

        if (page != null && page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (size != null && (size < 1 || size > 100)) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be less than zero");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }

        int effectiveLimit = (size != null) ? size : limit;
        int effectiveOffset = (page != null) ? page * effectiveLimit : offset;
        int effectivePage = effectiveOffset / effectiveLimit;

        PageResponse<DriverTripResponse> response = driverTripService.listDriverTrips(
                principal.getUserId(),
                view,
                status,
                date,
                effectivePage,
                effectiveLimit,
                sort
        );

        return ApiResponse.success(response);
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "Get trip detail for current driver")
    public ApiResponse<DriverTripDetailResponse> getTripDetail(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        DriverTripDetailResponse response = driverTripService.getTripDetail(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/confirm")
    @Operation(summary = "Driver confirms assignment of the trip")
    public ApiResponse<DriverTripDetailResponse> confirmTrip(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        DriverTripDetailResponse response = driverTripService.confirmTrip(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/arrive")
    @Operation(summary = "Driver reports arrival at pickup location")
    public ApiResponse<DriverTripDetailResponse> arriveAtPickup(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        DriverTripDetailResponse response = driverTripService.arriveAtPickup(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/start")
    @Operation(summary = "Driver starts the trip")
    public ApiResponse<DriverTripDetailResponse> startTrip(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        DriverTripDetailResponse response = driverTripService.startTrip(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/finish")
    @Operation(summary = "Driver finishes the trip")
    public ApiResponse<DriverTripDetailResponse> finishTrip(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        DriverTripDetailResponse response = driverTripService.finishTrip(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/locations")
    @Operation(summary = "Driver reports live device geolocation")
    public ApiResponse<TripLocationResponse> recordLocation(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @Valid @RequestBody LocationUpdateRequest request) {
        TripLocationResponse response = driverTripService.recordLocation(principal.getUserId(), tripId, request);
        return ApiResponse.success(response);
    }

    @GetMapping("/{tripId}/tracking")
    @Operation(summary = "Get trip tracking and monitoring state")
    public ApiResponse<TripTrackingResponse> getTripTracking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        TripTrackingResponse response = driverTripService.getTripTracking(principal.getUserId(), tripId);
        return ApiResponse.success(response);
    }

    @GetMapping("/{tripId}/location")
    @Operation(summary = "Get latest reported location of the vehicle")
    public ApiResponse<TripLocationResponse> getTripLocation(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        TripTrackingResponse tracking = driverTripService.getTripTracking(principal.getUserId(), tripId);
        return ApiResponse.success(tracking.latestLocation());
    }

    @PostMapping("/{tripId}/reject")
    @Operation(summary = "Driver rejects assignment with mandatory reason")
    public ApiResponse<DriverTripDetailResponse> rejectTrip(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @Valid @RequestBody com.fptufa26.ecomotion.booking.dto.request.DriverRejectRequest request) {
        DriverTripDetailResponse response = driverTripService.rejectTrip(principal.getUserId(), tripId, request);
        return ApiResponse.success(response);
    }

    @PostMapping("/{tripId}/incidents")
    @Operation(summary = "Driver reports an incident during trip lifecycle")
    public ApiResponse<com.fptufa26.ecomotion.booking.entity.TripIncident> reportIncident(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @Valid @RequestBody com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest request) {
        return ApiResponse.success(driverTripService.reportIncident(principal.getUserId(), tripId, request));
    }

    @GetMapping("/{tripId}/incidents")
    @Operation(summary = "Get incidents reported for a trip")
    public ApiResponse<java.util.List<com.fptufa26.ecomotion.booking.entity.TripIncident>> listIncidents(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        return ApiResponse.success(driverTripService.listIncidents(principal.getUserId(), tripId));
    }

    @PostMapping(value = "/{tripId}/evidence/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Driver uploads trip evidence photo to Cloudinary")
    public ApiResponse<com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse> uploadEvidenceDirect(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("type") com.fptufa26.ecomotion.booking.enums.TripEvidenceType type,
            @RequestParam(value = "note", required = false) String note) {
        return ApiResponse.success(tripEvidenceService.uploadEvidence(
                principal.getUserId(),
                principal.getRole(),
                tripId,
                file,
                type,
                note
        ));
    }

    @DeleteMapping("/{tripId}/evidence/{evidenceId}")
    @Operation(summary = "Driver deletes uploaded trip evidence")
    public ApiResponse<String> deleteEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @PathVariable("evidenceId") Long evidenceId) {
        tripEvidenceService.deleteEvidence(principal.getUserId(), principal.getRole(), tripId, evidenceId);
        return ApiResponse.success("Đã xóa ảnh minh chứng thành công");
    }

    @PostMapping("/{tripId}/evidence")
    @Operation(summary = "Driver uploads trip handover, pickup, or completion evidence")
    public ApiResponse<com.fptufa26.ecomotion.booking.entity.TripEvidence> uploadEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @Valid @RequestBody com.fptufa26.ecomotion.booking.dto.request.TripEvidenceRequest request) {
        return ApiResponse.success(driverTripService.uploadEvidence(principal.getUserId(), tripId, request));
    }

    @GetMapping("/{tripId}/evidence")
    @Operation(summary = "Get evidence photos uploaded for a trip")
    public ApiResponse<java.util.List<com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse>> listEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId) {
        return ApiResponse.success(tripEvidenceService.listEvidence(principal.getUserId(), principal.getRole(), tripId));
    }
}
