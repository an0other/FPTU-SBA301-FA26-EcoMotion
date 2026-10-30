package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.VehicleLocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleLocationRepository extends JpaRepository<VehicleLocation, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT l FROM VehicleLocation l WHERE l.driver.id IN :driverIds " +
            "AND NOT EXISTS (SELECT newer.id FROM VehicleLocation newer WHERE newer.driver.id = l.driver.id " +
            "AND (newer.receivedAt > l.receivedAt OR (newer.receivedAt = l.receivedAt AND newer.id > l.id)))")
    List<VehicleLocation> findLatestForDrivers(@org.springframework.data.repository.query.Param("driverIds") List<Long> driverIds);

    Optional<VehicleLocation> findFirstByBooking_IdOrderByReceivedAtDesc(Long bookingId);

    Optional<VehicleLocation> findFirstByDriver_IdOrderByReceivedAtDesc(Long driverId);

    List<VehicleLocation> findByBooking_IdOrderByReceivedAtDesc(Long bookingId, Pageable pageable);

    long countByBooking_Id(Long bookingId);
}
