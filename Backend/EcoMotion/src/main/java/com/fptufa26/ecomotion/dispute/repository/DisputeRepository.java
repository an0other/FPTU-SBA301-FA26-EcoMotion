package com.fptufa26.ecomotion.dispute.repository;

import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.entity.DisputeCategory;
import com.fptufa26.ecomotion.dispute.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    boolean existsByBooking_IdAndCategoryAndStatusAndCharge_Id(Long bookingId,
                                                               DisputeCategory category,
                                                               DisputeStatus status,
                                                               Long chargeId);

    boolean existsByBooking_IdAndCategoryAndStatusAndChargeIsNull(Long bookingId,
                                                                  DisputeCategory category,
                                                                  DisputeStatus status);
}
