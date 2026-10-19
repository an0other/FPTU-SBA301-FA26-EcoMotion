package com.fptufa26.ecomotion.dispute.repository;

import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findAllByOrderByCreatedAtDesc();

    List<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);

    List<Dispute> findByBooking_Id(Long bookingId);

    long countByStatus(DisputeStatus status);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.status IN ('OPEN', 'IN_REVIEW')")
    long countOpenOrInReview();
}
