package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.dto.request.ReportDriverIssueRequest;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.booking.dto.request.DriverRejectRequest;
import com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest;
import com.fptufa26.ecomotion.booking.dto.request.TripEvidenceRequest;
import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.entity.TripIncident;
import java.util.List;

public interface DriverTripService {

    PageResponse<DriverTripResponse> listDriverTrips(
            Long currentUserId,
            String view,
            String status,
            String date,
            int page,
            int size,
            String sort);

    DriverTripDetailResponse getTripDetail(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse confirmTrip(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse arriveAtPickup(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse startTrip(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse finishTrip(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse reportUnavailable(Long currentUserId, String tripIdOrCode,
                                               ReportDriverIssueRequest request);

    TripLocationResponse recordLocation(Long currentUserId, String tripIdOrCode, LocationUpdateRequest request);

    TripTrackingResponse getTripTracking(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse rejectTrip(Long currentUserId, String tripIdOrCode, DriverRejectRequest request);

    TripIncident reportIncident(Long currentUserId, String tripIdOrCode, IncidentReportRequest request);

    List<TripIncident> listIncidents(Long currentUserId, String tripIdOrCode);

    TripEvidence uploadEvidence(Long currentUserId, String tripIdOrCode, TripEvidenceRequest request);

    List<TripEvidence> listEvidence(Long currentUserId, String tripIdOrCode);
}
