package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            join fetch b.vehicle v
            join fetch b.renter r
            join fetch r.user
            where v.provider.id = :providerId
            order by b.createdAt desc, b.id desc
            """)
    List<Booking> findProviderQueue(@Param("providerId") Long providerId);

    @Query("""
            select b from Booking b
            join fetch b.vehicle v
            join fetch b.renter r
            join fetch r.user
            where v.provider.id = :providerId
              and b.status = :status
            order by b.createdAt desc, b.id desc
            """)
    List<Booking> findProviderQueueByStatus(@Param("providerId") Long providerId,
                                            @Param("status") BookingStatus status);
}
