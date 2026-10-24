package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripEvidenceRepository extends JpaRepository<TripEvidence, Long> {

    List<TripEvidence> findByBooking_IdOrderByRecordedAtAsc(Long bookingId);

    List<TripEvidence> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);

    List<TripEvidence> findByBooking_IdAndEvidenceTypeOrderByRecordedAtAsc(Long bookingId, TripEvidenceType evidenceType);

    Optional<TripEvidence> findByIdAndBooking_Id(Long id, Long bookingId);
}

