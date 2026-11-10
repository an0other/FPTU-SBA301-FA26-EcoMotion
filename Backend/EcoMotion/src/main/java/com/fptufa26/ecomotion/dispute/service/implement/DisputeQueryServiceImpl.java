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
    public List<Dispute> findByBooking(Long bookingId) {
        return disputeRepository.findByBooking_Id(bookingId);
    }
}
