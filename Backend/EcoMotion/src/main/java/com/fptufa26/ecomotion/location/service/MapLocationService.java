package com.fptufa26.ecomotion.location.service;

import java.math.BigDecimal;
import java.util.List;
import com.fptufa26.ecomotion.location.dto.response.AddressSearchResult;

public interface MapLocationService {

    /** Great-circle distance in kilometers; all coordinates are in degrees. */
    double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2);

    String reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    List<AddressSearchResult> searchAddress(String query);
}
