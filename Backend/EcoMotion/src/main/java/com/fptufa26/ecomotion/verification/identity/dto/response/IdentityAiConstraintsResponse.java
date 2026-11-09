package com.fptufa26.ecomotion.verification.identity.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityAiConstraintsResponse(Integer maxImageBytes, Long maxImagePixels, Integer maxVideoBytes,
        Double maxVideoSeconds, Double maxVideoFps, Integer maxVideoFrames, Long maxVideoPixels,
        List<String> imageFormats, List<String> videoContainers, List<String> challenge, Double minimumHoldSeconds) {
}
