package com.fptufa26.ecomotion.dispute.dto.response;

import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DisputeResponse(Long id, DisputeCategory category, Long chargeId, String chargeDescription,
        String description, List<String> evidenceUrls, DisputeStatus status, String handledByName,
        String resolution, DisputeResolutionDecision decision, Instant createdAt, Instant resolvedAt,
        BigDecimal refundAwardTotal, List<Long> refundRequestIds, String financialStatus) { }
