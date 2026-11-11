package com.fptufa26.ecomotion.dispute.service.implement;

import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fptufa26.ecomotion.dispute.entity.Dispute;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeQueryServiceImpl implements DisputeQueryService {
    private final DisputeRepository disputeRepository;

    @Override
    public boolean hasActiveDispute(Long bookingId) {
        return disputeRepository.existsByBooking_IdAndStatusIn(bookingId,
                List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW));
    }

    @Override
    public boolean blocksPayment(Long bookingId, Long paymentRequestId) {
        return disputeRepository.findByBooking_Id(bookingId).stream()
                .filter(d -> d.getStatus() == DisputeStatus.OPEN || d.getStatus() == DisputeStatus.IN_REVIEW)
                .anyMatch(d -> switch (d.getCategory()) {
                    case SERVICE_QUALITY, OTHER -> false;
                    case PAYMENT_MISMATCH -> d.getPaymentRequestId() == null || paymentRequestId == null
                            || paymentRequestId.equals(d.getPaymentRequestId());
                    default -> true;
                });
    }

    @Override
    public List<Dispute> findByBooking(Long bookingId) {
        return disputeRepository.findByBooking_Id(bookingId);
    }
}
