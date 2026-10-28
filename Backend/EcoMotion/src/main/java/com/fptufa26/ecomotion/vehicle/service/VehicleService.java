package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleAvailabilityResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VehicleService {

    PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                           String fuelType, String planType,
                                                           BigDecimal maxPrice,
                                                           VehicleSort sort,
                                                           int offset, int limit);

    VehicleDetailResponse getVehicleDetail(Long id);

    String getCoverImageUrl(Long vehicleId);

    Map<Long, String> getCoverImageUrls(Collection<Long> vehicleIds);

    List<VehicleMapResponse> findNearbyVehicles(BigDecimal latitude, BigDecimal longitude,
                                                int radiusKm, String keyword, String location,
                                                String fuelType, String planType, BigDecimal maxPrice,
                                                VehicleSort sort);

    VehicleAvailabilityResponse getAvailability(Long vehicleId, Instant from, Instant to);

    void updateRating(Long vehicleId, BigDecimal ratingAvg, int ratingCount);

    // Admin methods (TV6 - A03)
    List<Vehicle> listAdminVehicles(VehicleStatus status, String keyword);

    Vehicle getAdminVehicle(Long id);

    List<VehicleDocument> getVehicleDocuments(Long vehicleId);

    Vehicle updateVehicleApproval(Long vehicleId, VehicleStatus status, String reason);

    long countVehiclesByStatus(VehicleStatus status);

    long countTotalVehicles();

    Optional<Vehicle> findVehicleById(Long id);

    Optional<Vehicle> findVehicleByIdAndProviderId(Long vehicleId, Long providerId);

    Optional<Vehicle> findVehicleByIdForUpdate(Long id);
    Vehicle requireApprovedProviderVehicleForUpdate(Long vehicleId, Long providerId);
}
