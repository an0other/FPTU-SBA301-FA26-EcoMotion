package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.VehicleLicensePlateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleLicensePlateHistoryRepository extends JpaRepository<VehicleLicensePlateHistory, Long> {
    List<VehicleLicensePlateHistory> findByVehicle_IdOrderByChangedAtDesc(Long vehicleId);
}
