package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.ProviderApplication;
import com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplication, Long> {
    Page<ProviderApplication> findByStatus(ProviderApplicationStatus status, Pageable pageable);
}
