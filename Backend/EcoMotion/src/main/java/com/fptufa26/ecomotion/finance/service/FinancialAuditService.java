package com.fptufa26.ecomotion.finance.service;

import java.time.Instant;
import java.util.List;

public interface FinancialAuditService {
    boolean replay(String type, Long id, String requestId, Long actor, String payload);
    void record(String type, Long id, String requestId, Long actor, String outcome, String payload, String previous);
    List<Entry> history(String type, Long id);
    record Entry(Long id, Long actorId, String outcome, String payload, String previousState, Instant createdAt) {}
}
