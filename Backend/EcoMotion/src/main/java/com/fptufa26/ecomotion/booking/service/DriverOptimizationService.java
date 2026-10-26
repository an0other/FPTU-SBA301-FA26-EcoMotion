package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.user.entity.Driver;

import java.util.List;

public interface DriverOptimizationService {

    List<DriverCandidateResponse> evaluateCandidates(Long bookingId, Long providerId);

    AssignmentSuggestionResponse suggestDriver(Long bookingId, Long providerId);

    Driver autoAssign(Long bookingId);

    Driver manualAssign(Long bookingId, Long driverId, Long providerId);

    void processPendingAssignmentTimeouts();

    void triggerAutoReassign(Long bookingId, Long rejectedDriverId, String reason);
}
