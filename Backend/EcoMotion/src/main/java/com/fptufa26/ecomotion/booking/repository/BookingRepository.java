package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    long countByBookingCodeStartingWith(String prefix);

    List<Booking> findByRenter_IdAndStatusIn(Long renterId, Collection<BookingStatus> statuses);
}
