package com.fptufa26.ecomotion.location.service;

import java.math.BigDecimal;
import java.util.List;
import com.fptufa26.ecomotion.location.dto.response.AddressSearchResult;
import com.fptufa26.ecomotion.location.dto.response.RoadRouteResponse;

public interface MapLocationService {

    /** Car route distance in kilometers; unavailable routes must not be replaced by straight lines. */
    double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2);

    RoadRouteResponse route(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude);

    String reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    List<AddressSearchResult> searchAddress(String query);
}
