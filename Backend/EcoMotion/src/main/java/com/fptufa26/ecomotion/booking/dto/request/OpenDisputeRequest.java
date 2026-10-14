package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import jakarta.validation.constraints.NotNull;

public record OpenDisputeRequest(
        @NotNull DisputeCategory category,
        ChargeType chargeType
) {
}
