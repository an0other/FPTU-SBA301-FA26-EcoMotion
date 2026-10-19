package com.fptufa26.ecomotion.booking.dto.response;

import java.math.BigDecimal;

public record DriverSnippetResponse(
        String fullName,
        String phone,
        String portraitUrl,
        BigDecimal ratingAvg,
        int ratingCount
) {
}
