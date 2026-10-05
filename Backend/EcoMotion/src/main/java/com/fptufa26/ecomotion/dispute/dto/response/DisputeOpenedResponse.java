package com.fptufa26.ecomotion.dispute.dto.response;

import com.fptufa26.ecomotion.dispute.entity.DisputeCategory;
import com.fptufa26.ecomotion.dispute.entity.DisputeStatus;

public record DisputeOpenedResponse(
        DisputeCategory category,
        DisputeStatus status
) {
}
