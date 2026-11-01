package com.fptufa26.ecomotion.booking.service.map;

import com.fptufa26.ecomotion.booking.service.MapGeocodingService.GeoPoint;

import java.util.Optional;

public interface MapGeocodingProvider {

    String getProviderName();

    Optional<GeoPoint> geocode(String query);
}
