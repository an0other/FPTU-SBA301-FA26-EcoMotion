package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.user.entity.Driver;

import java.util.List;

public interface DriverOptimizationService {

    List<DriverCandidateResponse> evaluateCandidates(Long bookingId, Long providerId);

    AssignmentSuggestionResponse suggestDriver(Long bookingId, Long providerId);

    java.time.Instant assignmentDeadline();

    Driver autoAssign(Long bookingId);

    AutoAssignment autoAssignWithResult(Long bookingId);

    record AutoAssignment(Driver driver, String solverStatus) {}

    Driver manualAssign(Long bookingId, Long driverId, Long providerId);

    /**
     * Bước ghi nhận phân công dùng chung cho Manual, Auto, Auto Suggest và tái phân công.
     * Điều kiện: caller đã khóa Booking (findByIdForUpdate) và Driver (findDriverByIdForUpdate)
     * và đã kiểm tra quyền sở hữu, eligibility, availability, buffer. Hàm này chỉ ghi trạng thái,
     * lịch sử DriverAssignment, đồng bộ trạng thái Driver và gửi thông báo sau commit.
     */
    com.fptufa26.ecomotion.booking.entity.Booking commitAssignment(
            com.fptufa26.ecomotion.booking.entity.Booking booking,
            Driver driver,
            com.fptufa26.ecomotion.booking.enums.AssignmentMode mode,
            java.time.Instant now);

    void recallActiveAssignments(Long bookingId);

    com.fptufa26.ecomotion.booking.entity.DriverAssignment requireCurrentAssignment(
            com.fptufa26.ecomotion.booking.entity.Booking booking, Long driverId);

    void processPendingAssignmentTimeouts();

    void triggerAutoReassign(Long bookingId, Long rejectedDriverId, String reason);
}
