package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    List<Driver> findByProvider_Id(Long providerId);

    List<Driver> findByProvider_IdOrderByIdAsc(Long providerId);

    List<Driver> findByProvider_IdAndStatus(Long providerId, DriverStatus status);

    long countByProvider_Id(Long providerId);

    long countByProvider_IdAndStatus(Long providerId, DriverStatus status);

    Optional<Driver> findByIdAndProvider_Id(Long id, Long providerId);

    boolean existsByLicenseNoIgnoreCase(String licenseNo);

    @Query("SELECT d FROM Driver d " +
            "JOIN FETCH d.user u " +
            "WHERE d.provider.id = :providerId " +
            "AND d.status = :status " +
            "AND d.licenseExpiry >= :currentDate " +
            "ORDER BY d.ratingAvg DESC, d.ratingCount DESC")
    List<Driver> findEligibleDrivers(
            @Param("providerId") Long providerId,
            @Param("status") DriverStatus status,
            @Param("currentDate") LocalDate currentDate
    );

    @Query("SELECT d FROM Driver d " +
            "JOIN FETCH d.user u " +
            "WHERE d.provider.id = :providerId " +
            "ORDER BY d.ratingAvg DESC")
    List<Driver> findByProvider_IdWithUser(@Param("providerId") Long providerId);
}
