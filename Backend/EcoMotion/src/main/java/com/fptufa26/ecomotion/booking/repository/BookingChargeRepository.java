package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingChargeRepository
        extends JpaRepository<BookingCharge, Long> {

    List<BookingCharge> findByBooking_IdOrderByCreatedAtAscIdAsc(
            Long bookingId);
}