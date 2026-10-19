package com.fptufa26.ecomotion.vehicle.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface VehicleImageService {

    List<String> findImageUrls(Long vehicleId);

    Map<Long, List<String>> findImageUrlsByVehicleIds(Collection<Long> vehicleIds);
}
