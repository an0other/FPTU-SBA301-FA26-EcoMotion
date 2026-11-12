package com.fptufa26.ecomotion.finance.service;
import com.fptufa26.ecomotion.finance.dto.request.RefundAccountRequest;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
public interface RefundEventService {
    int updateRecipientAccount(Long bookingId, RefundAccountRequest account);
    BigDecimal reservedForEvent(Long bookingId, String eventId);
    boolean hasOpenRefunds(Long bookingId);
    boolean hasUnsettledYearlyRefunds(Long bookingId);
    /** Còn khoản khách chuyển dư chưa hoàn xong (hoặc khách báo chưa nhận) — chặn hoàn tất đơn. */
    boolean hasOpenExcessRefunds(Long bookingId);
    boolean isSourceReserved(Long sourceTransactionId);
    List<RefundRequest> reserve(Long bookingId, String sourceEventId, BigDecimal amount, String reason, User actor);
    List<RefundRequest> reserveOnTransaction(Long bookingId, String sourceEventId, BigDecimal amount, String reason, User actor, Long sourceTransactionId);
    List<RefundRequest> awardDepositRefund(Long bookingId, Long disputeId, BigDecimal award, User actor);
    BigDecimal openAllocated(Long bookingId, BigDecimal obligation);
    BigDecimal remainingReceived(Long bookingId);
    void submitGateway(Long refundId);
    RefundRequest releaseForFollowUp(Long refundId, User actor, boolean manual);
    GatewaySubmission prepareGateway(Long refundId);
    void applyGateway(Long refundId, String requestId, GatewayResult result);
    record GatewayResult(boolean timedOut, boolean checksumValid, String responseCode, String transactionStatus, String refundTransactionNo, String txnRef, String amount) {}
    record GatewaySubmission(String requestId, String txnRef, String transactionNo, String payDate, String type, long minor, String orderInfo) {}
}
