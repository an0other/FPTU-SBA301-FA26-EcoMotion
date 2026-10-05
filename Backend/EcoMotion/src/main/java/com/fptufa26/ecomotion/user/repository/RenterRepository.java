package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Renter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RenterRepository extends JpaRepository<Renter, Long> {

    Optional<Renter> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
