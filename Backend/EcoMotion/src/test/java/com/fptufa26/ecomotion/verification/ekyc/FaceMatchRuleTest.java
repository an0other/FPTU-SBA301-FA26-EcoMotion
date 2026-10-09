package com.fptufa26.ecomotion.verification.ekyc;

import com.fptufa26.ecomotion.verification.ekyc.EkycService.FaceMatchResult;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaceMatchRuleTest {

    @Test
    void acceptsAConfidentMatch() {
        FaceMatchResult result = FaceMatchRule.decide(Boolean.TRUE, 98.7, 70);

        assertThat(result.match()).isTrue();
        assertThat(result.similarity()).isEqualTo(98.7);
    }

    @Test
    void rejectsAConfidentMismatch() {
        assertThat(FaceMatchRule.decide(Boolean.FALSE, 12.5, 70).match()).isFalse();
    }

    @Test
    void rejectsAProviderClaimOfMatchBelowTheThreshold() {
        assertThatThrownBy(() -> FaceMatchRule.decide(Boolean.TRUE, 40.0, 70))
                .isInstanceOf(EkycFailedException.class)
                .hasMessageContaining("contradictory");
    }

    @Test
    void rejectsAProviderClaimOfMismatchAboveTheThreshold() {
        assertThatThrownBy(() -> FaceMatchRule.decide(Boolean.FALSE, 90.0, 70))
                .isInstanceOf(EkycFailedException.class)
                .hasMessageContaining("contradictory");
    }

    @Test
    void rejectsAMissingSimilarityScore() {
        assertThatThrownBy(() -> FaceMatchRule.decide(Boolean.TRUE, null, 70))
                .isInstanceOf(EkycFailedException.class)
                .hasMessageContaining("similarity");
    }

    @Test
    void usesTheThresholdWhenTheProviderOmitsTheFlag() {
        assertThat(FaceMatchRule.decide(null, 80.0, 70).match()).isTrue();
        assertThat(FaceMatchRule.decide(null, 60.0, 70).match()).isFalse();
    }

    @Test
    void readsProviderValuesFromStringsAndNumbers() {
        assertThat(FaceMatchRule.toBoolean("true")).isTrue();
        assertThat(FaceMatchRule.toBoolean("FALSE")).isFalse();
        assertThat(FaceMatchRule.toBoolean("yes")).isNull();
        assertThat(FaceMatchRule.toBoolean(null)).isNull();
        assertThat(FaceMatchRule.toDouble(91)).isEqualTo(91.0);
        assertThat(FaceMatchRule.toDouble("91")).isNull();
    }
}
