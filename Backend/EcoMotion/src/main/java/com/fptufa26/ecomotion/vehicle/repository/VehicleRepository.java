package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    List<Vehicle> findByStatusOrderByIdAsc(VehicleStatus status);

    Optional<Vehicle> findByIdAndStatus(Long id, VehicleStatus status);

    List<Vehicle> findByProvider_IdOrderByIdAsc(Long providerId);

    Optional<Vehicle> findByIdAndProvider_Id(Long id, Long providerId);

    long countByProvider_Id(Long providerId);

    long countByProvider_IdAndStatus(Long providerId, VehicleStatus status);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Long id);

    boolean existsByChassisNoIgnoreCase(String chassisNo);

    boolean existsByEngineNoIgnoreCase(String engineNo);
}
