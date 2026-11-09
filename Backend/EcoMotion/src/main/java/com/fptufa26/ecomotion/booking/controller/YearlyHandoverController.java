package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.service.YearlyHandoverService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class YearlyHandoverController {
    private final YearlyHandoverService service;
    public record Arrival(@NotNull @Min(0) Long initialKm, @NotNull @Min(1) Long evidenceId) {}

    @GetMapping({"/api/renter/yearly-bookings/{code}/handover", "/api/provider/yearly-bookings/{code}/handover"})
    @PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
    public ApiResponse<YearlyHandoverService.Handover> get(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(service.get(user.getUserId(), code));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/handover/dispatch")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyHandoverService.Handover> dispatch(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(service.dispatch(user.getUserId(), code));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/handover/arrive")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyHandoverService.Handover> arrive(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody Arrival request) {
        return ApiResponse.success(service.arrive(user.getUserId(), code, request.initialKm(), request.evidenceId()));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/handover/receive")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyHandoverService.Handover> receive(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(service.receive(user.getUserId(), code));
    }
}
