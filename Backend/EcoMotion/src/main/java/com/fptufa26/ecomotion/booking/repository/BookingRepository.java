package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    Optional<Booking> findByBookingCode(String bookingCode);

    List<Booking> findByRenter_IdOrderByCreatedAtDesc(Long renterId);

    List<Booking> findByVehicle_IdOrderByCreatedAtDesc(Long vehicleId);

    long countByStatus(BookingStatus status);
}
