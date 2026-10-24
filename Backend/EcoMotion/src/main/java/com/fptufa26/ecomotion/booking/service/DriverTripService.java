package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;

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

    TripLocationResponse recordLocation(Long currentUserId, String tripIdOrCode, LocationUpdateRequest request);

    TripTrackingResponse getTripTracking(Long currentUserId, String tripIdOrCode);

    DriverTripDetailResponse rejectTrip(Long currentUserId, String tripIdOrCode, com.fptufa26.ecomotion.booking.dto.request.DriverRejectRequest request);

    com.fptufa26.ecomotion.booking.entity.TripIncident reportIncident(Long currentUserId, String tripIdOrCode, com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest request);

    java.util.List<com.fptufa26.ecomotion.booking.entity.TripIncident> listIncidents(Long currentUserId, String tripIdOrCode);

    com.fptufa26.ecomotion.booking.entity.TripEvidence uploadEvidence(Long currentUserId, String tripIdOrCode, com.fptufa26.ecomotion.booking.dto.request.TripEvidenceRequest request);

    java.util.List<com.fptufa26.ecomotion.booking.entity.TripEvidence> listEvidence(Long currentUserId, String tripIdOrCode);
}
