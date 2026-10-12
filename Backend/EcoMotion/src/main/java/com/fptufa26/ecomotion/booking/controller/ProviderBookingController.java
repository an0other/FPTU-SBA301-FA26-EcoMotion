package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/provider/bookings")
@RequiredArgsConstructor
@Tag(name = "Provider Bookings", description = "Provider booking queue (O07)")
public class ProviderBookingController {

    private final ProviderBookingService providerBookingService;

    @GetMapping
    @Operation(summary = "List the current provider's bookings",
            description = "Bookings of vehicles owned by the authenticated provider (resolved from "
                    + "the JWT). Optional `status` = PENDING | REJECTED | ACCEPTED | CONFIRMED | "
                    + "IN_PROGRESS | COMPLETED | CANCELLED. Omit to get all. Invalid status -> 400.")
    public ApiResponse<List<ProviderBookingResponse>> listBookings(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(providerBookingService.listBookings(principal.getUserId(), status));
    }
}
