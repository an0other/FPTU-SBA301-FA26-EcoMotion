package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.service.FinancialAuditService;
import com.fptufa26.ecomotion.finance.repository.FinancialReviewRecordRepository;
import com.fptufa26.ecomotion.finance.entity.FinancialReviewRecord;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialAuditServiceImpl implements FinancialAuditService {
    private final FinancialReviewRecordRepository records;
    private final Clock clock;
    @Override
    @Transactional(readOnly = true)
    public boolean replay(String type, Long id, String requestId, Long actor, String payload) {
        var old = records.findByTargetTypeAndTargetIdAndRequestId(type, id, requestId);
        if (old.isEmpty()) return false;
        if (!old.get().getActorId().equals(actor) || !old.get().getPayload().equals(payload))
            throw new ConflictException("Mã yêu cầu đã dùng cho nội dung khác");
        return true;
    }
    @Override
    @Transactional
    public void record(String type, Long id, String requestId, Long actor, String outcome, String payload, String previous) {
        var row = new FinancialReviewRecord();
        row.setTargetType(type); row.setTargetId(id); row.setRequestId(requestId); row.setActorId(actor);
        row.setOutcome(outcome); row.setPayload(payload); row.setPreviousState(previous); row.setCreatedAt(clock.instant());
        records.saveAndFlush(row);
    }
    @Override
    @Transactional(readOnly = true)
    public List<Entry> history(String type, Long id) {
        return records.findByTargetTypeAndTargetIdOrderByIdAsc(type, id).stream()
                .map(r -> new Entry(r.getId(), r.getActorId(), r.getOutcome(), r.getPayload(), r.getPreviousState(), r.getCreatedAt())).toList();
    }
}
