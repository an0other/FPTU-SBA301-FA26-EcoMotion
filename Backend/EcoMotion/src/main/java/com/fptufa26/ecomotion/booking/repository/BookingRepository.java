package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingCodeAndRenter_User_Id(
            String bookingCode,
            Long userId);

    boolean existsByBookingCode(String bookingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findBookingByBookingCodeAndRenter_User_Id(
            String bookingCode,
            Long userId);
}