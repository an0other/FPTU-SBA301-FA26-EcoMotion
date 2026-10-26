package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import org.springframework.web.multipart.MultipartFile;

public interface RefundService {

    RefundProofResponse completeRefund(Long currentUserId, Long transactionId, MultipartFile proof,
                                       String bankReference, String note);

    PaymentEvidenceFileResponse getProof(Long currentUserId, Long transactionId);
}
