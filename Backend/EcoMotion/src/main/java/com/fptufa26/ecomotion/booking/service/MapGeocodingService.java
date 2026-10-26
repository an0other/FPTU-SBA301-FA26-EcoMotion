package com.fptufa26.ecomotion.booking.service;

import java.util.Optional;

public interface MapGeocodingService {

    record GeoPoint(double latitude, double longitude, String address) {
        public GeoPoint(double latitude, double longitude) {
            this(latitude, longitude, null);
        }
    }

    Optional<GeoPoint> geocodeAddress(String address);
}
