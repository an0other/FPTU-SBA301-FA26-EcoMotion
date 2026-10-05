package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.ChargeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {

    Optional<BookingCharge> findByBooking_IdAndChargeType(Long bookingId, ChargeType chargeType);
}
