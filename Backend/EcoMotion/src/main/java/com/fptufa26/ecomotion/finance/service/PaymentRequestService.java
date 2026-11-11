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
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;
import com.fptufa26.ecomotion.finance.dto.request.BankTransferReconciliationRequest;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.user.entity.User;

public interface PaymentRequestService {
    record YearlyCash(BigDecimal rentPaid, BigDecimal depositHeld) {}
    YearlyCash yearlyCash(Long bookingId);
    void finalizeYearlyObligations(Long bookingId, BigDecimal balance);
    boolean yearlyBalancePaid(Long bookingId, BigDecimal balance);
    void recordYearlyRefund(Long bookingId, BigDecimal rent, BigDecimal deposit, String reference, String evidenceUrl, Long renterUserId);
    void recordYearlyRefundByAdmin(Long bookingId, BigDecimal rent, BigDecimal deposit, String reference, String evidenceUrl, Long adminId);

    boolean yearlyInitialPaymentsComplete(Long bookingId);

    void rebaseYearlyInstallments(Long bookingId, java.time.LocalDate start);

    List<YearlyPaymentItem> yearlyPayments(Long userId, String bookingCode);

    record YearlyPaymentItem(Long id, String purpose, int installmentNumber,
            java.time.LocalDate periodStart, java.time.LocalDate periodEnd,
            BigDecimal amount, BigDecimal paidAmount, String status, String transferReference, java.time.LocalDate dueOn, boolean overdue, BigDecimal remainingAmount) {}

    Optional<PaymentRequest> findByBookingIdAndPaymentType(Long bookingId, PaymentRequestType paymentType);

    PaymentRequest savePaymentRequest(PaymentRequest paymentRequest);

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

    default PageResponse<PaymentRequestResponse> listProviderPaymentRequests(Long currentUserId, String status, String bookingCode, int offset, int limit) {
        return listProviderPaymentRequests(currentUserId, status, offset, limit);
    }

    PaymentRequestResponse confirmPayment(Long paymentRequestId, Long currentUserId, ConfirmPaymentRequest request);

    PaymentRequestResponse recordBankTransferForReconciliation(Long paymentRequestId, Long currentUserId,
            BankTransferReconciliationRequest request);

    PaymentRequestResponse rejectPayment(Long paymentRequestId, Long currentUserId, RejectPaymentRequest request);

    PaymentRequestResponse reportIssue(Long paymentRequestId, Long currentUserId, ReportIssueRequest request);

    SimulatePaymentResponse simulatePayment(Long paymentRequestId, Long adminUserId, SimulatePaymentRequest request);

    void seedSandboxData(Long adminUserId);

    boolean expireDepositWithoutProof(Long bookingId);

    Optional<PaymentRequest> findPaymentRequest(Long bookingId, PaymentRequestType type);

    void voidUnpaidDepositIfProviderWithdraws(Long bookingId);

    java.util.List<RefundRequest> applyMismatchResolution(
            Long paymentRequestId, java.math.BigDecimal verifiedTotal, User admin, Long disputeId);

    PaymentRequestResponse acknowledgeReceipt(Long paymentRequestId, Long currentUserId);
}
