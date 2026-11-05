package com.fptufa26.ecomotion.booking.repository;
import com.fptufa26.ecomotion.booking.entity.TripEndConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
public interface TripEndConfirmationRepository extends JpaRepository<TripEndConfirmation,Long> {
    List<TripEndConfirmation> findByBooking_IdOrderByIdDesc(Long bookingId);
    Optional<TripEndConfirmation> findFirstByBooking_IdOrderByIdDesc(Long bookingId);
    List<TripEndConfirmation> findByStateAndDeadlineLessThanEqual(String state, Instant now);
}
