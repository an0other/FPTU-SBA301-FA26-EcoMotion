package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportIssueRequest;
import com.fptufa26.ecomotion.finance.dto.request.SimulatePaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.SubmitProofRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.dto.response.SimulatePaymentResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;

import java.util.List;

public interface PaymentRequestService {

    PaymentRequest createDepositPaymentRequest(Booking booking);

    PaymentRequest createFinalBalancePaymentRequest(Long bookingId);

    List<PaymentRequestResponse> getPaymentRequestsByBooking(Long bookingId, Long currentUserId);

    PaymentRequestResponse getPaymentRequestDetail(Long paymentRequestId, Long currentUserId);

    PaymentEvidenceResponse submitPaymentProof(Long paymentRequestId, Long currentUserId, SubmitProofRequest request);

    List<ProviderTransactionResponse> getBookingPaymentHistory(Long bookingId, Long currentUserId);

    PageResponse<PaymentRequestResponse> listProviderPaymentRequests(Long currentUserId, String status, int offset, int limit);

    PaymentRequestResponse confirmPayment(Long paymentRequestId, Long currentUserId, ConfirmPaymentRequest request);

    PaymentRequestResponse rejectPayment(Long paymentRequestId, Long currentUserId, RejectPaymentRequest request);

    PaymentRequestResponse reportIssue(Long paymentRequestId, Long currentUserId, ReportIssueRequest request);

    SimulatePaymentResponse simulatePayment(Long paymentRequestId, Long adminUserId, SimulatePaymentRequest request);

    void seedSandboxData(Long adminUserId);
}
