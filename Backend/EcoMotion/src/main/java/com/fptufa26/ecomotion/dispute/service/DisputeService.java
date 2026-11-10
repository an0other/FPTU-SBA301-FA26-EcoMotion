package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import com.fptufa26.ecomotion.user.entity.User;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

public interface DisputeService {
    void resolveYearlyReview(Long disputeId, Long adminId, String resolution);

    DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge);

    DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge,
                               String description, List<String> evidenceUrls);

    /** Gắn chi tiết khiếu nại sai lệch thanh toán (yêu cầu thanh toán, số khách khai, loại sai lệch). */
    void attachPaymentMismatch(Long disputeId, Long paymentRequestId, BigDecimal claimedAmount, String mismatchType);

    /** Tổng tiền nhà xe đã ghi nhận (giao dịch vào đã xác nhận) cho một yêu cầu thanh toán. */
    BigDecimal recordedForPaymentRequest(Long bookingId, Long paymentRequestId);

    List<Dispute> listDisputes(DisputeStatus status);

    Optional<Dispute> getDisputeById(Long disputeId);

    Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                           DisputeResolutionDecision decision, String resolution);

    Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                           DisputeResolutionDecision decision, String resolution, BigDecimal refundAwardTotal);

    Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                           DisputeResolutionDecision decision, String resolution, BigDecimal refundAwardTotal, String tripEndAction);

    long countOpenOrInReview();

    long countTotalDisputes();

    boolean hasActiveDispute(Long bookingId);

    List<Dispute> getDisputesByBookingId(Long bookingId);
    boolean hasOpenDispute(Long bookingId);
}
