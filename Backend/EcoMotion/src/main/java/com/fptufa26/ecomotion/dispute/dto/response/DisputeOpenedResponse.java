package com.fptufa26.ecomotion.dispute.dto.response;

import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;

public record DisputeOpenedResponse(
        DisputeCategory category,
        DisputeStatus status
) {
}
