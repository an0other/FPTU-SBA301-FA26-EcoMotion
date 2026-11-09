package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.YearlyReplacement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YearlyReplacementRepository extends JpaRepository<YearlyReplacement, Long> {

    java.util.List<YearlyReplacement> findByBookingIdOrderByIdAsc(Long bookingId);
    java.util.Optional<YearlyReplacement> findByIdAndBookingId(Long id, Long bookingId);
}
