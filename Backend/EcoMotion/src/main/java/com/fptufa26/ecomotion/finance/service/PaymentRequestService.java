package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportIssueRequest;
import com.fptufa26.ecomotion.finance.dto.request.SimulatePaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.SubmitProofRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.dto.response.SimulatePaymentResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;

public interface PaymentRequestService {

    PaymentRequest createDepositPaymentRequest(Booking booking);

    PaymentRequest createFinalBalancePaymentRequest(Long bookingId);

    BigDecimal calculateRemainingAmount(Long bookingId);

    List<PaymentRequestResponse> getPaymentRequestsByBooking(Long bookingId, Long currentUserId);

    List<PaymentRequestResponse> getPaymentRequestsByBookingCode(String bookingCode, Long currentUserId);

    PaymentRequestResponse getPaymentRequestDetail(Long paymentRequestId, Long currentUserId);

    PaymentRequestResponse reissuePaymentRequest(Long paymentRequestId, Long currentUserId);

    PaymentRequestResponse reissueDepositByBookingCode(String bookingCode, Long currentUserId);

    PaymentEvidenceResponse submitPaymentProof(Long paymentRequestId, Long currentUserId, SubmitProofRequest request);

    PaymentEvidenceResponse submitPaymentProof(
            Long paymentRequestId, Long currentUserId, MultipartFile file,
            BigDecimal transferredAmount, String transactionReference,
            Instant transferredAt, String note);

    PaymentEvidenceFileResponse getPaymentEvidenceFile(Long evidenceId, Long currentUserId);

    List<ProviderTransactionResponse> getBookingPaymentHistory(Long bookingId, Long currentUserId);

    PageResponse<PaymentRequestResponse> listProviderPaymentRequests(Long currentUserId, String status, int offset, int limit);

    PaymentRequestResponse confirmPayment(Long paymentRequestId, Long currentUserId, ConfirmPaymentRequest request);

    PaymentRequestResponse rejectPayment(Long paymentRequestId, Long currentUserId, RejectPaymentRequest request);

    PaymentRequestResponse reportIssue(Long paymentRequestId, Long currentUserId, ReportIssueRequest request);

    SimulatePaymentResponse simulatePayment(Long paymentRequestId, Long adminUserId, SimulatePaymentRequest request);

    void seedSandboxData(Long adminUserId);

    boolean expireDepositWithoutProof(Long bookingId);

    Optional<PaymentRequest> findPaymentRequest(Long bookingId, com.fptufa26.ecomotion.finance.enums.PaymentRequestType type);
}
