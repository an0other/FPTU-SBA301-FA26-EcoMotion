package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.dispute.entity.Dispute;

public interface DisputeQueryService {
    boolean hasActiveDispute(Long bookingId);

    boolean blocksPayment(Long bookingId, Long paymentRequestId);

    java.util.List<Dispute> findByBooking(Long bookingId);
}
