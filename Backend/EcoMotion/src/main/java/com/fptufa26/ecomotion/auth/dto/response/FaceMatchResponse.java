package com.fptufa26.ecomotion.auth.dto.response;

public record FaceMatchResponse(
        boolean matched,
        double similarity,
        String portraitUrl,
        boolean activated,
        String message
) {
}
