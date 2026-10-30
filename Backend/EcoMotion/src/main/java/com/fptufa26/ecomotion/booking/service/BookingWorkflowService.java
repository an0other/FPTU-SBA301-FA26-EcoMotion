package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest;
import com.fptufa26.ecomotion.booking.dto.request.RespondOperationalProposalRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingWorkflowResponse;
import com.fptufa26.ecomotion.booking.dto.response.OperationalResolutionResponse;

public interface BookingWorkflowService {
    BookingWorkflowResponse getWorkflow(Long currentUserId, String bookingCode);
    OperationalResolutionResponse proposeAlternative(Long currentUserId, String bookingCode, ResolveOperationalRiskRequest request);
    BookingWorkflowResponse respondToProposal(Long currentUserId, String bookingCode, String proposalId, RespondOperationalProposalRequest request);
    BookingWorkflowResponse withdrawProposal(Long currentUserId, String bookingCode, String proposalId);
}
