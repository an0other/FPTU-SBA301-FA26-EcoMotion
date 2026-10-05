package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
