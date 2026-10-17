package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findByProvider_IdOrderByIdAsc(Long providerId);

    long countByProvider_Id(Long providerId);

    Optional<Driver> findByIdAndProvider_Id(Long id, Long providerId);

    Optional<Driver> findByUser_Id(Long userId);

    boolean existsByLicenseNoIgnoreCase(String licenseNo);
}
