package com.fptufa26.ecomotion.review.repository;

import com.fptufa26.ecomotion.review.entity.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"booking", "vehicle", "driver"})
    List<Review> findByBooking_BookingCodeOrderByCreatedAtAscIdAsc(
            String bookingCode);

    boolean existsByBooking_IdAndVehicle_Id(
            Long bookingId,
            Long vehicleId);

    boolean existsByBooking_IdAndDriver_Id(
            Long bookingId,
            Long driverId);

    List<Review> findByVehicle_Id(Long vehicleId);

    List<Review> findByDriver_Id(Long driverId);

    List<Review> findByVehicle_Provider_IdOrDriver_Provider_Id(Long vehicleProviderId, Long driverProviderId);

    Optional<Review> findByBooking_IdAndVehicle_Id(Long bookingId, Long vehicleId);

    Optional<Review> findByBooking_IdAndDriver_Id(Long bookingId, Long driverId);

}