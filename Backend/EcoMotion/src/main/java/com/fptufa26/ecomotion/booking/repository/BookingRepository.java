package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends
        JpaRepository<Booking, Long>,
        JpaSpecificationExecutor<Booking> {

    @Override
    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    List<Booking> findAll(@Nullable Specification<Booking> spec);

    @Override
    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    List<Booking> findAll(@Nullable Specification<Booking> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    Page<Booking> findAll(@Nullable Specification<Booking> spec, Pageable pageable);

    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    java.util.Optional<Booking> findByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    java.util.Optional<Booking> findByBookingCodeAndVehicle_Provider_Id(String bookingCode, Long providerId);

    @EntityGraph(attributePaths = {
            "vehicle",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    java.util.Optional<Booking> findByIdAndVehicle_Provider_Id(Long id, Long providerId);

    List<Booking> findByVehicle_Provider_Id(Long providerId);

    Page<Booking> findByVehicle_Provider_Id(Long providerId, Pageable pageable);

    long countByVehicle_Provider_Id(Long providerId);

    long countByVehicle_Provider_IdAndStatus(Long providerId, com.fptufa26.ecomotion.booking.enums.BookingStatus status);

    long countByVehicle_Provider_IdAndStatusIn(Long providerId, java.util.Collection<com.fptufa26.ecomotion.booking.enums.BookingStatus> statuses);

    long countByDriver_IdAndStatusIn(Long driverId, java.util.Collection<com.fptufa26.ecomotion.booking.enums.BookingStatus> statuses);

    @EntityGraph(attributePaths = {
            "vehicle",
            "vehicle.provider",
            "vehicle.provider.user",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    java.util.Optional<Booking> findByIdAndDriver_Id(Long id, Long driverId);

    @EntityGraph(attributePaths = {
            "vehicle",
            "vehicle.provider",
            "vehicle.provider.user",
            "renter",
            "renter.user",
            "driver",
            "driver.user",
            "plan"
    })
    java.util.Optional<Booking> findByBookingCodeAndDriver_Id(String bookingCode, Long driverId);
}
