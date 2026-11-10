package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.dispute.entity.Dispute;

public interface DisputeQueryService {
    boolean hasActiveDispute(Long bookingId);

    java.util.List<Dispute> findByBooking(Long bookingId);
}
