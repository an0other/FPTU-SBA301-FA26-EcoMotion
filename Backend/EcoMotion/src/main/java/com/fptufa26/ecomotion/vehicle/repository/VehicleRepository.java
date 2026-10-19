package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findAllByOrderByIdDesc();

    List<Vehicle> findByStatusOrderByIdAsc(VehicleStatus status);

    List<Vehicle> findByStatusOrderByIdDesc(VehicleStatus status);

    Optional<Vehicle> findByIdAndStatus(Long id, VehicleStatus status);

    List<Vehicle> findByProvider_IdOrderByIdAsc(Long providerId);

    long countByStatus(VehicleStatus status);
}
