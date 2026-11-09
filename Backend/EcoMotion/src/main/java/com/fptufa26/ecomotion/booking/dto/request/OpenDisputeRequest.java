package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record OpenDisputeRequest(
        @NotNull DisputeCategory category,
        Long chargeId,
        ChargeType chargeType,
        String description,
        List<String> evidenceUrls,
        String evidenceUrl
) {
    public OpenDisputeRequest(DisputeCategory category, Long chargeId) {
        this(category, chargeId, null, null, null, null);
    }

    public OpenDisputeRequest(DisputeCategory category, ChargeType chargeType) {
        this(category, null, chargeType, null, null, null);
    }

    public OpenDisputeRequest(DisputeCategory category, Long chargeId, String description) {
        this(category, chargeId, null, description, null, null);
    }

    public List<String> resolvedEvidenceUrls() {
        List<String> result = new ArrayList<>();
        if (evidenceUrls != null && !evidenceUrls.isEmpty()) {
            result.addAll(evidenceUrls.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList());
        }
        if (evidenceUrl != null && !evidenceUrl.isBlank() && !result.contains(evidenceUrl.trim())) {
            result.add(evidenceUrl.trim());
        }
        // Chỉ nhận ảnh do hệ thống lưu (/api/files/...) hoặc https; tối đa 5 ảnh.
        return result.stream()
                .filter(url -> url.length() <= 500 && !url.chars().anyMatch(Character::isWhitespace)
                        && (url.startsWith("/api/files/booking/") || url.startsWith("https://")))
                .distinct()
                .limit(5)
                .toList();
    }
}
