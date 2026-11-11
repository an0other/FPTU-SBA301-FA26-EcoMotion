package com.fptufa26.ecomotion.verification.identity.dto.response;

import java.util.List;

public record IdentityConstraintsResponse(Integer maxImageBytes, Long maxImagePixels, Integer maxVideoBytes,
        Double maxVideoSeconds, Double maxVideoFps, Integer maxVideoFrames, Long maxVideoPixels,
        List<String> imageFormats, List<String> videoContainers, List<String> challenge, Double minimumHoldSeconds,
        boolean verificationEnabled, boolean acceptanceConfigured) { }
