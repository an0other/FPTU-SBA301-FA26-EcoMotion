package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.entity.TripIncident;
import com.fptufa26.ecomotion.booking.enums.TripIncidentStatus;
import com.fptufa26.ecomotion.booking.enums.TripIncidentType;
import com.fptufa26.ecomotion.booking.enums.TripIncidentUrgency;

import java.time.Instant;

public record TripIncidentResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        Long driverId,
        TripIncidentType incidentType,
        String description,
        TripIncidentUrgency urgency,
        TripIncidentStatus status,
        Double locationLatitude,
        Double locationLongitude,
        String evidenceUrls,
        String resolutionNote,
        Instant createdAt,
        Instant resolvedAt
) {
    public static TripIncidentResponse fromEntity(TripIncident incident) {
        return new TripIncidentResponse(
                incident.getId(),
                incident.getBooking() != null ? incident.getBooking().getId() : null,
                incident.getBooking() != null ? incident.getBooking().getBookingCode() : null,
                incident.getDriver() != null ? incident.getDriver().getId() : null,
                incident.getIncidentType(),
                incident.getDescription(),
                incident.getUrgency(),
                incident.getStatus(),
                incident.getLocationLatitude(),
                incident.getLocationLongitude(),
                incident.getEvidenceUrls(),
                incident.getResolutionNote(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}
