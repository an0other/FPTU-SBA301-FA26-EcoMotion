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
import java.util.Optional;

public interface DisputeService {

    DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge);

    List<Dispute> listDisputes(DisputeStatus status);

    Optional<Dispute> getDisputeById(Long disputeId);

    Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status,
                           DisputeResolutionDecision decision, String resolution);

    long countOpenOrInReview();

    long countTotalDisputes();
}
