package com.fptufa26.ecomotion.user.repository;

import com.fptufa26.ecomotion.user.entity.ProviderApplicationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProviderApplicationReviewRepository extends JpaRepository<ProviderApplicationReview, Long> {
    List<ProviderApplicationReview> findByUserIdOrderByIdAsc(Long userId);
}
