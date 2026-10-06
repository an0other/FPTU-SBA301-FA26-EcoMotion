package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Renter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RenterRepository extends JpaRepository<Renter, Long> {

    Optional<Renter> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    /**
     * The {@code cccd_no} column is two-way encrypted by an {@code AttributeConverter}, so the
     * plaintext is converted before the comparison — same behaviour as {@code existsByPhone}.
     */
    boolean existsByCccdNo(String cccdNo);
}
