package com.fptufa26.ecomotion.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripLocationResponse(
        Double latitude,
        Double longitude,
        Double accuracyMeters,
        Instant recordedAt,
        Instant receivedAt,
        boolean isStale,
        String trackingState
) {}
