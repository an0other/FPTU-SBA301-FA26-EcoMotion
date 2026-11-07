package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.YearlyIncident;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YearlyIncidentRepository extends JpaRepository<YearlyIncident, Long> {

    java.util.List<YearlyIncident> findByBookingIdOrderByIdAsc(Long bookingId);
    java.util.Optional<YearlyIncident> findByIdAndBookingId(Long id, Long bookingId);
    java.util.Optional<YearlyIncident> findByBookingIdAndRequestId(Long bookingId, String requestId);
}
