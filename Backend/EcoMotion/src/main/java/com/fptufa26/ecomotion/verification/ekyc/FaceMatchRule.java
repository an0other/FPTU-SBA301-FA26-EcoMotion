package com.fptufa26.ecomotion.verification.ekyc;

import com.fptufa26.ecomotion.verification.ekyc.EkycService.FaceMatchResult;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;

public final class FaceMatchRule {

    private FaceMatchRule() {
    }

    public static FaceMatchResult decide(Boolean providerMatched, Double similarity, double minSimilarity) {
        if (similarity == null) {
            throw new EkycFailedException("eKYC provider did not return a similarity score");
        }
        boolean aboveThreshold = similarity >= minSimilarity;
        if (providerMatched != null && providerMatched != aboveThreshold) {
            throw new EkycFailedException("eKYC provider returned a contradictory face match result");
        }
        return new FaceMatchResult(aboveThreshold, similarity);
    }

    public static Boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public static Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
