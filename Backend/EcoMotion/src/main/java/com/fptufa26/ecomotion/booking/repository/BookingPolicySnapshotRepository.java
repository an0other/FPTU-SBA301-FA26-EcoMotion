package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingPolicySnapshotRepository
        extends JpaRepository<BookingPolicySnapshot, Long> {

    Optional<BookingPolicySnapshot> findByBooking_Id(Long bookingId);
}