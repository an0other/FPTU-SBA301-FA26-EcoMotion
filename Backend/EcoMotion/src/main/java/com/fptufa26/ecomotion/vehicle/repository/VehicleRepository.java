package com.fptufa26.ecomotion.vehicle.repository;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findAllByOrderByIdDesc();

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    List<Vehicle> findByStatusOrderByIdAsc(VehicleStatus status);

    List<Vehicle> findByStatusOrderByIdDesc(VehicleStatus status);

    Optional<Vehicle> findByIdAndStatus(Long id, VehicleStatus status);

    List<Vehicle> findByProvider_IdOrderByIdAsc(Long providerId);

    Optional<Vehicle> findByIdAndProvider_Id(Long id, Long providerId);

    long countByProvider_Id(Long providerId);

    long countByProvider_IdAndStatus(Long providerId, VehicleStatus status);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Long id);

    boolean existsByChassisNoIgnoreCase(String chassisNo);

    boolean existsByEngineNoIgnoreCase(String engineNo);

    long countByStatus(VehicleStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
}
