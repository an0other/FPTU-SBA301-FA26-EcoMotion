package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.ConfirmCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.response.CommissionOverviewResponse;
import com.fptufa26.ecomotion.finance.dto.response.CommissionStatementResponse;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public interface CommissionService {

    CommissionOverviewResponse overview(Long providerUserId);

    CommissionStatementResponse providerStatement(Long providerUserId, Long statementId);

    CommissionStatementResponse reportPayment(Long providerUserId, Long statementId, ReportCommissionPaymentRequest request,
                                              org.springframework.web.multipart.MultipartFile proof);

    com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse providerProof(Long providerUserId, Long statementId, Long paymentId, boolean dispute);

    com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse adminProof(Long statementId, Long paymentId, boolean dispute);

    CommissionStatementResponse disputePayment(Long providerUserId, Long statementId, Long paymentId, java.math.BigDecimal claimedAmount, String note,
                                               org.springframework.web.multipart.MultipartFile proof);

    CommissionStatementResponse resolvePaymentDispute(Long adminUserId, Long statementId, Long paymentId, boolean accept, String note);

    List<CommissionStatementResponse> adminStatements(String periodMonth, CommissionStatementStatus status);

    CommissionStatementResponse adminStatement(Long statementId);

    CommissionStatementResponse rejectReport(Long statementId, String note);

    CommissionStatementResponse confirmPayment(Long adminUserId, Long statementId, ConfirmCommissionPaymentRequest request);

    int generate(YearMonth month);

    int markOverdue();

    boolean isBookingBlocked(Long providerId);

    Set<Long> blockedProviderIds();
}
