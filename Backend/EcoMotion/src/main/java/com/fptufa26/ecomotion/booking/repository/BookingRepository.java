package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByVehicle_Provider_IdOrderByCreatedAtDesc(Long providerId);

    List<Booking> findByVehicle_Provider_IdAndStatusOrderByCreatedAtDesc(Long providerId, BookingStatus status);
}
