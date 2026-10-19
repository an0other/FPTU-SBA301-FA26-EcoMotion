package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;

import java.util.List;
import java.util.Optional;

public interface DisputeService {

    List<Dispute> listDisputes(DisputeStatus status);

    Optional<Dispute> getDisputeById(Long disputeId);

    Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status, String resolution);

    long countOpenOrInReview();

    long countTotalDisputes();
}
