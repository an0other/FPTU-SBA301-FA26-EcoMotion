package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest;
import com.fptufa26.ecomotion.booking.dto.response.AvailableDriverResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.dto.response.SettlementResultResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.booking.enums.DriverAssignmentMode;
import com.fptufa26.ecomotion.booking.dto.request.ResolveOperationalRiskRequest;
import com.fptufa26.ecomotion.booking.dto.response.OperationalResolutionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import com.fptufa26.ecomotion.booking.dto.request.ResolveIncidentRequest;
import com.fptufa26.ecomotion.booking.dto.response.AssignmentSuggestionResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripIncidentResponse;

public interface ProviderBookingService {

    List<ProviderBookingResponse> listBookings(Long currentUserId, String status);

    Page<ProviderBookingResponse> listBookings(Long currentUserId, String status, Pageable pageable);

    PageResponse<ProviderBookingResponse> listBookingsPaged(Long currentUserId, String status, int offset, int limit);

    ProviderBookingSummaryResponse getSummary(Long currentUserId);

    ProviderBookingDetailResponse getBookingDetail(Long currentUserId, String bookingCodeOrId);

    ProviderBookingResponse acceptBooking(Long currentUserId, String bookingCodeOrId);

    ProviderBookingResponse denyBooking(Long currentUserId, String bookingCodeOrId, String reason);

    ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId, Long driverId);

    ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId,
                                         Long driverId, DriverAssignmentMode mode);

    List<BookingChargeResponse> getCharges(Long currentUserId, String bookingCodeOrId);

    BookingChargeResponse addCharge(Long currentUserId, String bookingCodeOrId, AddChargeRequest request);

    SettlementResultResponse finalizeSettlement(Long currentUserId, String bookingCodeOrId);

    List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId);

    List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId, String bookingCode);

    byte[] exportBookingsCsv(Long currentUserId, String status);

    TripTrackingResponse getTripTracking(Long currentUserId, String bookingCodeOrId);

    TripLocationResponse getTripLocation(Long currentUserId, String bookingCodeOrId);

    OperationalResolutionResponse resolveOperationalRisk(
            Long currentUserId, String bookingCodeOrId, ResolveOperationalRiskRequest request);

    List<DriverCandidateResponse> getAssignmentCandidates(Long currentUserId, String bookingCodeOrId);

    AssignmentSuggestionResponse suggestDriverAssignment(Long currentUserId, String bookingCodeOrId);

    ProviderBookingResponse autoAssignDriver(Long currentUserId, String bookingCodeOrId);

    void resolveIncident(Long currentUserId, String bookingCodeOrId, Long incidentId, ResolveIncidentRequest request);

    List<TripIncidentResponse> getBookingIncidents(Long currentUserId, String bookingCodeOrId);

    List<TripEvidenceResponse> getBookingEvidence(Long currentUserId, String bookingCodeOrId);
}
