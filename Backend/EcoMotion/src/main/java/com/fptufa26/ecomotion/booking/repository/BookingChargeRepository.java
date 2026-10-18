package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingChargeRepository extends JpaRepository<BookingCharge, Long> {

    @Query("SELECT c FROM BookingCharge c " +
            "LEFT JOIN FETCH c.createdBy " +
            "WHERE c.booking.id = :bookingId " +
            "ORDER BY c.createdAt ASC")
    List<BookingCharge> findByBookingIdWithUser(@Param("bookingId") Long bookingId);

    Optional<BookingCharge> findByIdAndBooking_Id(Long id, Long bookingId);
}
