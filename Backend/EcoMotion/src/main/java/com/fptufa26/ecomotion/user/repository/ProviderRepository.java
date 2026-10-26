package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Provider> findProviderById(Long id);

    List<Provider> findAllByOrderByCreatedAtDesc();

    List<Provider> findByStatusOrderByCreatedAtDesc(ProviderStatus status);

    long countByStatus(ProviderStatus status);
}
