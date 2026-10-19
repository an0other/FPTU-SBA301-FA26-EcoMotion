package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findAllByOrderByCreatedAtDesc();

    List<Driver> findByStatusOrderByCreatedAtDesc(DriverStatus status);

    Optional<Driver> findByUser_Id(Long userId);

    List<Driver> findByProvider_Id(Long providerId);

    long countByStatus(DriverStatus status);
}
