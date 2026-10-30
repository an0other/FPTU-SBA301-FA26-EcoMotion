package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.config.OptimizationProperties;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.service.implement.DriverSelectionServiceImpl;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NativeDriverSelectionTest {
    private DriverCandidateResponse candidate(long id, int eta, long workload, String rating, boolean eligible) {
        return new DriverCandidateResponse(id, id, "Driver " + id, null, null, null, null,
                null, new BigDecimal(rating), 1, eta, 1.0, workload, 0.0, eligible,
                eligible ? null : "SCHEDULE_CONFLICT", null, false);
    }

    @Test
    void nativeCpSatSelectsFromTwoEligibleCandidates() {
        var properties = new OptimizationProperties();
        var service = new DriverSelectionServiceImpl(properties);
        var result = service.select(List.of(candidate(1, 10, 0, "4.8", true), candidate(2, 5, 1, "5.0", true)));
        assertThat(result.status()).isEqualTo("OPTIMAL");
        assertThat(result.candidate().driverId()).isEqualTo(2L);
    }

    @Test
    void configuredObjectiveNeverSelectsIneligibleDriver() {
        var properties = new OptimizationProperties();
        properties.setWeightWorkload(1000);
        var service = new DriverSelectionServiceImpl(properties);
        var result = service.select(List.of(candidate(1, 10, 0, "4.8", true), candidate(2, 5, 1, "5", true),
                candidate(3, 0, 0, "5", false)));
        assertThat(result.status()).isEqualTo("OPTIMAL");
        assertThat(result.candidate().driverId()).isEqualTo(1L);
    }

    @Test
    void noEligibleCandidateReturnsNoAssignment() {
        var result = new DriverSelectionServiceImpl(new OptimizationProperties())
                .select(List.of(candidate(1, 1, 0, "5", false)));
        assertThat(result.status()).isEqualTo("INFEASIBLE");
        assertThat(result.candidate()).isNull();
    }
    @Test
    void invalidSolverParametersUseExplicitFallbackAndKeepEligibility() {
        var properties = new OptimizationProperties();
        properties.setMaxSolverTimeSeconds(-1);
        var result = new DriverSelectionServiceImpl(properties).select(List.of(
                candidate(1, 10, 0, "4.8", true), candidate(2, 5, 1, "5", true), candidate(3, 0, 0, "5", false)));
        assertThat(result.status()).isEqualTo("FALLBACK_MODEL_INVALID");
        assertThat(result.candidate().driverId()).isEqualTo(2L);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
        }
    }

}
