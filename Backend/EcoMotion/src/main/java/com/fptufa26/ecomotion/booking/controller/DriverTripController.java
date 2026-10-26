package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.dto.request.ReportDriverIssueRequest;
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

    @PostMapping("/{tripId}/report-unavailable")
    @Operation(summary = "Tài xế báo không thể tiếp tục nhận chuyến do sự cố")
    public ApiResponse<DriverTripDetailResponse> reportUnavailable(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("tripId") String tripId,
            @Valid @RequestBody ReportDriverIssueRequest request) {
        return ApiResponse.success(driverTripService.reportUnavailable(principal.getUserId(), tripId, request));
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
}
