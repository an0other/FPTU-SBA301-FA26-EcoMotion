package com.fptufa26.ecomotion.booking.dto.response;

import java.time.Instant;

public record TimelineStepResponse(
        String title,
        Instant timestamp,
        String description,
        boolean completed,
        boolean failed
) {
}
