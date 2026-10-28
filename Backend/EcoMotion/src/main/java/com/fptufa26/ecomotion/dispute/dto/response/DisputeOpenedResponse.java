package com.fptufa26.ecomotion.dispute.dto.response;

import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;

import java.util.List;

public record DisputeOpenedResponse(
        DisputeCategory category,
        DisputeStatus status,
        Long disputeId,
        String description,
        List<String> evidenceUrls
) {
    public DisputeOpenedResponse(DisputeCategory category, DisputeStatus status) {
        this(category, status, null, null, null);
    }
}
