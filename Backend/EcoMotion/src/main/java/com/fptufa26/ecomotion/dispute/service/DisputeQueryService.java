package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.dispute.entity.Dispute;

public interface DisputeQueryService {
    boolean hasActiveDispute(Long bookingId);

    /**
     * Khiếu nại đang mở có chặn khoản thanh toán này không. Chỉ chặn khoản bị tranh chấp:
     * CHARGE/TRIP_NOT_CONFIRMED chặn quyết toán; PAYMENT_MISMATCH chặn đúng yêu cầu bị khiếu nại;
     * SERVICE_QUALITY/OTHER không chặn; các loại còn lại (huỷ, gói năm) chặn mọi khoản.
     */
    boolean blocksPayment(Long bookingId, Long paymentRequestId, boolean finalBalance);

    java.util.List<Dispute> findByBooking(Long bookingId);
}
