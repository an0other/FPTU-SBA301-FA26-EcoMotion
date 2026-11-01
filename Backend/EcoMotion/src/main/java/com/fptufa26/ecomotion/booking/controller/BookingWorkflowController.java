package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingWorkflowResponse;
import com.fptufa26.ecomotion.booking.service.BookingWorkflowService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookingWorkflowController {
    private final BookingWorkflowService workflowService;

    @GetMapping({"/api/renter/bookings/{code}/workflow", "/api/provider/bookings/{code}/workflow"})
    @PreAuthorize("hasAnyRole('RENTER', 'PROVIDER', 'ADMIN')")
    public ApiResponse<BookingWorkflowResponse> getWorkflow(@AuthenticationPrincipal SecurityUser user, @PathVariable String code) {
        return ApiResponse.success(workflowService.getWorkflow(user.getUserId(), code));
    }

    @PutMapping("/api/renter/bookings/{code}/operational-proposal/{proposalId}")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<BookingWorkflowResponse> respond(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable String proposalId,
            @Valid @RequestBody RespondOperationalProposalRequest request) {
        return ApiResponse.success(workflowService.respondToProposal(user.getUserId(), code, proposalId, request));
    }

    @DeleteMapping("/api/provider/bookings/{code}/operational-proposal/{proposalId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<BookingWorkflowResponse> withdraw(@AuthenticationPrincipal SecurityUser user,
            @PathVariable String code, @PathVariable String proposalId) {
        return ApiResponse.success(workflowService.withdrawProposal(user.getUserId(), code, proposalId));
    }
}
