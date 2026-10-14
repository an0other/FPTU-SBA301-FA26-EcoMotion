package com.fptufa26.ecomotion.vehicle.service;

import java.math.BigDecimal;
import java.util.List;
import com.fptufa26.ecomotion.vehicle.dto.response.AddressSearchResult;

public interface MapLocationService {

    String reverseGeocode(BigDecimal latitude, BigDecimal longitude);

    List<AddressSearchResult> searchAddress(String query);
}
