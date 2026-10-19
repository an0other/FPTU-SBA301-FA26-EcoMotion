package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingPolicySnapshotRepository extends JpaRepository<BookingPolicySnapshot, Long> {

    Optional<BookingPolicySnapshot> findByBooking_Id(Long bookingId);
}
