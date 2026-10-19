package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.AddBookingChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.AssignDriverRequest;
import com.fptufa26.ecomotion.booking.dto.request.RejectBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provider/bookings")
public class ProviderBookingController {

    private final ProviderBookingService providerBookingService;

    public ProviderBookingController(ProviderBookingService providerBookingService) {
        this.providerBookingService = providerBookingService;
    }

    /**
     * O07: Get list of bookings in queue with optional status filter
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderBookingResponse>>> getBookings(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) BookingStatus status) {
        List<ProviderBookingResponse> bookings = providerBookingService.getProviderBookings(principal.getUserId(), status);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    /**
     * O08: Feature 1 - Get full booking details for authenticated provider
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<ProviderBookingDetailResponse>> getBookingDetail(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        ProviderBookingDetailResponse detail = providerBookingService.getBookingDetail(principal.getUserId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * O08: Feature 2 - Accept booking request (PENDING -> ACCEPTED)
     */
    @PatchMapping("/{bookingId}/accept")
    public ResponseEntity<ApiResponse<ProviderBookingDetailResponse>> acceptBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        ProviderBookingDetailResponse response = providerBookingService.acceptBooking(principal.getUserId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * O08: Feature 3 - Reject booking request (PENDING -> REJECTED)
     */
    @PatchMapping("/{bookingId}/reject")
    public ResponseEntity<ApiResponse<ProviderBookingDetailResponse>> rejectBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId,
            @RequestBody(required = false) RejectBookingRequest request) {
        ProviderBookingDetailResponse response = providerBookingService.rejectBooking(principal.getUserId(), bookingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * O08: Feature 4 - Get available drivers for booking time window
     */
    @GetMapping("/{bookingId}/available-drivers")
    public ResponseEntity<ApiResponse<List<DriverAvailabilityResponse>>> getAvailableDriversForBooking(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId) {
        List<DriverAvailabilityResponse> drivers = providerBookingService.getAvailableDrivers(principal.getUserId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }

    /**
     * O08: Feature 5 - Assign or change driver for booking
     */
    @PatchMapping("/{bookingId}/driver")
    public ResponseEntity<ApiResponse<ProviderBookingDetailResponse>> assignDriver(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId,
            @Valid @RequestBody AssignDriverRequest request) {
        ProviderBookingDetailResponse response = providerBookingService.assignDriver(principal.getUserId(), bookingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * O08: Feature 6 - Add manual booking charge (surcharge)
     */
    @PostMapping("/{bookingId}/charges")
    public ResponseEntity<ApiResponse<BookingChargeResponse>> addManualCharge(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId,
            @Valid @RequestBody AddBookingChargeRequest request) {
        BookingChargeResponse response = providerBookingService.addManualCharge(principal.getUserId(), bookingId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * O08: Feature 7 - Remove manual booking charge
     */
    @DeleteMapping("/{bookingId}/charges/{chargeId}")
    public ResponseEntity<ApiResponse<Void>> removeManualCharge(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingId,
            @PathVariable Long chargeId) {
        providerBookingService.removeManualCharge(principal.getUserId(), bookingId, chargeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
