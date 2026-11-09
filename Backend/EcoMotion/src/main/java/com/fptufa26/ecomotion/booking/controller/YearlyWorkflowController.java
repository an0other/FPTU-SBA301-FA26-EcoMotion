package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.service.YearlyWorkflowService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class YearlyWorkflowController {
    private final YearlyWorkflowService service;

    @PostMapping("/api/renter/yearly-bookings/{code}/returns/preview")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.Preview> preview(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.PreviewRequest request) {
        return ApiResponse.success(service.preview(user.getUserId(), code, request));
    }


    @PostMapping({"/api/renter/yearly-bookings/{code}/replacements/{id}/cancel", "/api/provider/yearly-bookings/{code}/replacements/{id}/cancel"})
    @PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
    public ApiResponse<YearlyWorkflowService.Replacement> cancelReplacement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.cancelReplacement(user.getUserId(), code, id, request));
    }


    @GetMapping({"/api/renter/yearly-bookings/{code}/workflow", "/api/provider/yearly-bookings/{code}/workflow"})
    @PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> get(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(service.get(user.getUserId(), code));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/terms")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> terms(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Terms request) {
        return ApiResponse.success(service.terms(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/terms/accept")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> acceptTerms(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.acceptTerms(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/returns")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> requestReturn(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Return request) {
        return ApiResponse.success(service.requestReturn(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/returns/inspection")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> inspectReturn(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Inspection request) {
        return ApiResponse.success(service.inspectReturn(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/returns/confirm")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> confirmReturn(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.confirmReturn(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/settlement")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> proposeSettlement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Settlement request) {
        return ApiResponse.success(service.proposeSettlement(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/settlement/accept")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> acceptSettlement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.acceptSettlement(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/refund/transfer")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> refundTransfer(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Transfer request) {
        return ApiResponse.success(service.refundTransfer(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/refund/confirm")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.View> confirmRefund(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.confirmRefund(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/complete")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> complete(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.complete(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/reopen")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.View> reopen(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Reopen request) {
        return ApiResponse.success(service.reopen(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/incidents")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.Incident> report(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Report request) {
        return ApiResponse.success(service.report(user.getUserId(), code, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/incidents/{id}/resolution")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.Incident> resolve(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Resolution request) {
        return ApiResponse.success(service.resolve(user.getUserId(), code, id, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/incidents/{id}/transfer")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.Incident> incidentTransfer(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Transfer request) {
        return ApiResponse.success(service.incidentTransfer(user.getUserId(), code, id, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/incidents/{id}/confirm")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.Incident> incidentConfirm(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.incidentConfirm(user.getUserId(), code, id, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/replacements")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.Replacement> replacement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @Valid @RequestBody YearlyWorkflowService.Replace request) {
        return ApiResponse.success(service.replacement(user.getUserId(), code, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/replacements/{id}/accept")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.Replacement> acceptReplacement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.acceptReplacement(user.getUserId(), code, id, request));
    }

    @PostMapping("/api/provider/yearly-bookings/{code}/replacements/{id}/inspection")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<YearlyWorkflowService.Replacement> replacementInspection(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Exchange request) {
        return ApiResponse.success(service.replacementInspection(user.getUserId(), code, id, request));
    }

    @PostMapping("/api/renter/yearly-bookings/{code}/replacements/{id}/confirm")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyWorkflowService.Replacement> confirmReplacement(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable Long id, @Valid @RequestBody YearlyWorkflowService.Version request) {
        return ApiResponse.success(service.confirmReplacement(user.getUserId(), code, id, request));
    }
}
