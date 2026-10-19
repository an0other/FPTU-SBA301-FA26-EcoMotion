package com.fptufa26.ecomotion.booking.service;

import java.util.Optional;

public interface MapGeocodingService {

    record GeoPoint(double latitude, double longitude) {}

    Optional<GeoPoint> geocodeAddress(String address);
}
