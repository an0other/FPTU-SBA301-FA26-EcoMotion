package com.fptufa26.ecomotion.location.dto.response;
import java.time.Instant;
import java.util.List;
public record RoadRouteResponse(double distanceMeters, long durationSeconds, List<List<Double>> coordinates, String source, Instant quotedAt) { }
