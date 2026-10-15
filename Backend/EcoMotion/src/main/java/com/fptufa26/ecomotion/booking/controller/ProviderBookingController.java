package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
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
@RequestMapping("/api/provider/bookings")
public class ProviderBookingController {

    private final ProviderBookingService providerBookingService;

    public ProviderBookingController(ProviderBookingService providerBookingService) {
        this.providerBookingService = providerBookingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderBookingResponse>>> getBookings(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) BookingStatus status) {
        List<ProviderBookingResponse> bookings = providerBookingService.getProviderBookings(principal.getUserId(), status);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }
}
