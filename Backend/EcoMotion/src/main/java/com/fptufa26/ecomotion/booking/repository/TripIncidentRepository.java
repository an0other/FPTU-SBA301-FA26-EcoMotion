package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.TripIncident;
import com.fptufa26.ecomotion.booking.enums.TripIncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripIncidentRepository extends JpaRepository<TripIncident, Long> {

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT i.driver.id FROM TripIncident i " +
            "WHERE i.driver.id IN :driverIds AND i.status IN :statuses")
    List<Long> findBlockedDriverIds(@org.springframework.data.repository.query.Param("driverIds") List<Long> driverIds,
            @org.springframework.data.repository.query.Param("statuses") List<TripIncidentStatus> statuses);

    List<TripIncident> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    List<TripIncident> findByDriver_IdOrderByCreatedAtDesc(Long driverId);

    List<TripIncident> findByBooking_IdAndStatus(Long bookingId, TripIncidentStatus status);

    boolean existsByDriver_IdAndStatusIn(Long driverId, List<TripIncidentStatus> statuses);

    boolean existsByBooking_IdAndStatusIn(Long bookingId, List<TripIncidentStatus> statuses);
}
