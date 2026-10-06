package com.fptufa26.ecomotion.auth.dto.response;

/**
 * Result of the face-match step: the uploaded selfie compared against the portrait on the CCCD
 * front image. {@code similarity} is a 0–100 score (mock providers return a fixed value).
 *
 * <p>This is the final activation step, so {@code activated} reports whether the account was
 * flipped to ACTIVE as part of the same call.</p>
 */
public record FaceMatchResponse(
        boolean matched,
        double similarity,
        String portraitUrl,
        boolean activated,
        String message
) {
}
