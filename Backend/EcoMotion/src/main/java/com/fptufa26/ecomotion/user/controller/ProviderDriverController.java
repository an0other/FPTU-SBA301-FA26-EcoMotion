package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.booking.dto.response.AvailableDriverResponse;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provider/drivers")
@RequiredArgsConstructor
@Tag(name = "Provider Drivers", description = "Provider driver management")
public class ProviderDriverController {

    private final ProviderBookingService providerBookingService;

    @GetMapping(value = "/available", params = "!bookingId")
    @Operation(summary = "Get available drivers for booking assignment")
    public ApiResponse<List<AvailableDriverResponse>> getAvailableDrivers(
            @AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.success(providerBookingService.getAvailableDrivers(principal.getUserId()));
    }
}
