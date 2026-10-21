package com.fptufa26.ecomotion.schedule.controller;

import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleItemResponse;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleStatsResponse;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/provider/schedules")
@PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
@Tag(name = "Provider Schedule Management", description = "Provider schedule blocking and maintenance (O05)")
@RequiredArgsConstructor
public class ProviderScheduleController {

    private final ProviderScheduleService providerScheduleService;

    @GetMapping("/stats")
    @Operation(summary = "Get provider schedule summary KPIs (O05)")
    public ApiResponse<ProviderScheduleStatsResponse> getScheduleStats(
            @AuthenticationPrincipal SecurityUser user
    ) {
        return ApiResponse.success(providerScheduleService.getScheduleStats(user.getUserId()));
    }

    @GetMapping
    @Operation(summary = "Get provider schedules with filters (O05)")
    public ApiResponse<List<ProviderScheduleItemResponse>> getSchedules(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) ScheduleType type,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate
    ) {
        return ApiResponse.success(providerScheduleService.getProviderSchedules(
                user.getUserId(),
                vehicleId,
                type,
                status,
                fromDate,
                toDate
        ));
    }

    @PostMapping
    @Operation(summary = "Create a new maintenance or personal block schedule (O05)")
    public ApiResponse<ProviderScheduleItemResponse> createBlockSchedule(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CreateBlockScheduleRequest request
    ) {
        return ApiResponse.success(providerScheduleService.createBlockSchedule(user.getUserId(), request));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Unblock/cancel a block schedule to restore vehicle availability (O05)")
    public ApiResponse<ProviderScheduleItemResponse> cancelBlockSchedule(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(providerScheduleService.cancelBlockSchedule(user.getUserId(), id));
    }
}
