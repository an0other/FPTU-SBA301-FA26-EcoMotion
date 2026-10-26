package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record OpenDisputeRequest(
        @NotNull DisputeCategory category,
        ChargeType chargeType,
        String description,
        List<String> evidenceUrls,
        String evidenceUrl
) {
    public OpenDisputeRequest(DisputeCategory category, ChargeType chargeType) {
        this(category, chargeType, null, null, null);
    }

    public OpenDisputeRequest(DisputeCategory category, ChargeType chargeType, String description) {
        this(category, chargeType, description, null, null);
    }

    public List<String> resolvedEvidenceUrls() {
        List<String> result = new ArrayList<>();
        if (evidenceUrls != null && !evidenceUrls.isEmpty()) {
            result.addAll(evidenceUrls.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList());
        }
        if (evidenceUrl != null && !evidenceUrl.isBlank() && !result.contains(evidenceUrl.trim())) {
            result.add(evidenceUrl.trim());
        }
        return result;
    }
}
