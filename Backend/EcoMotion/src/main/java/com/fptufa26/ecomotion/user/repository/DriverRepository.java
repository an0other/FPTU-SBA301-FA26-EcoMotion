package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUser_Id(Long userId);
    boolean existsByUser_Id(Long userId);
    java.util.List<Driver> findByProvider_Id(Long providerId);
    java.util.List<Driver> findByProvider_IdAndStatus(Long providerId, com.fptufa26.ecomotion.user.enums.DriverStatus status);
    Optional<Driver> findByIdAndProvider_Id(Long id, Long providerId);
}
