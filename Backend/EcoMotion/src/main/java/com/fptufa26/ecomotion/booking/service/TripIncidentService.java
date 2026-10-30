package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest;
import com.fptufa26.ecomotion.booking.dto.request.ResolveIncidentRequest;
import com.fptufa26.ecomotion.booking.entity.TripIncident;

import java.util.List;

public interface TripIncidentService {

    TripIncident reportIncident(Long currentUserId, String tripIdOrCode, IncidentReportRequest request);

    List<TripIncident> listIncidentsForDriver(Long currentUserId, String tripIdOrCode);

    void resolveIncident(Long currentUserId, String bookingCodeOrId, Long incidentId, ResolveIncidentRequest request);

    List<TripIncident> getBookingIncidentsForProvider(Long currentUserId, String bookingCodeOrId);

    TripIncident getIncidentById(Long incidentId);
}
