package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.AssignmentMode;

import java.util.List;

public record AssignmentSuggestionResponse(
        String bookingCode,
        AssignmentMode assignmentMode,
        String solverStatus,
        DriverCandidateResponse suggestedDriver,
        List<DriverCandidateResponse> candidates,
        String explanation
) {
}
