package com.fptufa26.ecomotion.dispute.service.implement;

import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> listDisputes(DisputeStatus status) {
        if (status != null) {
            return disputeRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return disputeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dispute> getDisputeById(Long disputeId) {
        return disputeRepository.findById(disputeId);
    }

    @Override
    @Transactional
    public Dispute resolveDispute(Long disputeId, Long adminUserId, DisputeStatus status, String resolution) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tranh chấp không tồn tại: " + disputeId));

        if (adminUserId != null) {
            User admin = userRepository.findById(adminUserId).orElse(null);
            dispute.setHandledBy(admin);
        }

        dispute.setStatus(status != null ? status : DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);

        return disputeRepository.save(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenOrInReview() {
        return disputeRepository.countOpenOrInReview();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalDisputes() {
        return disputeRepository.count();
    }
}
