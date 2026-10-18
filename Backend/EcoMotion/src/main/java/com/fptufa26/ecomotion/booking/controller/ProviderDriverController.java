package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provider/drivers")
public class ProviderDriverController {

    private final ProviderBookingService providerBookingService;

    public ProviderDriverController(ProviderBookingService providerBookingService) {
        this.providerBookingService = providerBookingService;
    }

    /**
     * O08 Feature 4: Query available drivers for a specific booking
     * GET /api/provider/drivers/available?bookingId={bookingId}
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<DriverAvailabilityResponse>>> getAvailableDrivers(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam String bookingId) {
        List<DriverAvailabilityResponse> drivers = providerBookingService.getAvailableDrivers(principal.getUserId(), bookingId);
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }
}
