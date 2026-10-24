package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;

import java.util.List;

public interface RefundService {

    RefundResponse createRefundRequest(Long currentUserId, CreateRefundRequest request);

    RefundResponse reviewRefundRequest(Long currentUserId, Long refundRequestId, ReviewRefundRequest request);

    List<RefundResponse> listRefundsByBooking(Long currentUserId, String bookingCodeOrId);

    List<RefundResponse> listRefundsForProvider(Long currentUserId);
}
