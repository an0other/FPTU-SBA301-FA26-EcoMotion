package com.fptufa26.ecomotion.booking.service;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import static org.assertj.core.api.Assertions.assertThat;

class DepositRefundBoundaryTest {
    @ParameterizedTest
    @CsvSource({"HOURLY,86400,1", "DAILY,86400,1", "DAILY,86399,0.5", "MONTHLY,2592000,1", "MONTHLY,2591999,0.5", "YEARLY,9999999,0", "DAILY,-1,0.5"})
    void exactCancellationPolicyBoundary(PlanType plan,long seconds,String expected) {
        Instant now=Instant.parse("2026-11-05T00:00:00Z");
        assertThat(DepositRefundRules.ratio(plan,now,now.plusSeconds(seconds))).isEqualByComparingTo(expected);
    }
}
