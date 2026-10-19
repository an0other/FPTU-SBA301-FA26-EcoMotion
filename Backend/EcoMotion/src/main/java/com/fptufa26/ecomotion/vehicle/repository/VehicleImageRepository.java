package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

    List<VehicleImage> findByVehicle_IdOrderByDisplayOrderAsc(Long vehicleId);

    boolean existsByVehicle_Id(Long vehicleId);

    List<VehicleImage> findByVehicle_IdInOrderByDisplayOrderAsc(Collection<Long> vehicleIds);

    Optional<VehicleImage> findByVehicle_IdAndDisplayOrder(Long vehicleId, Integer displayOrder);
}
