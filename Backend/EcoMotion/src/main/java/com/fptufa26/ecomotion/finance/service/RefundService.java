package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RefundService {

    RefundResponse createRefundRequest(Long currentUserId, CreateRefundRequest request);

    RefundResponse reviewRefundRequest(Long currentUserId, Long refundRequestId, ReviewRefundRequest request);

    List<RefundResponse> listRefundsByBooking(Long currentUserId, String bookingCodeOrId);

    List<RefundResponse> listRefundsForProvider(Long currentUserId);

    RefundProofResponse completeRefund(Long currentUserId, Long transactionId, MultipartFile proof,
                                       String bankReference, String note);

    PaymentEvidenceFileResponse getProof(Long currentUserId, Long transactionId);
}
