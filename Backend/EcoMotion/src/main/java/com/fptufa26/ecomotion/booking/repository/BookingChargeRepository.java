package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {
    List<BookingCharge> findByBooking_IdOrderByCreatedAtAsc(Long bookingId);
}
