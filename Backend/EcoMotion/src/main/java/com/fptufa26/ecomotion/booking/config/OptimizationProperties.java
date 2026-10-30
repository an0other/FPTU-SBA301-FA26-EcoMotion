package com.fptufa26.ecomotion.booking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.optimization")
public class OptimizationProperties {
    @Positive
    private double maxSolverTimeSeconds = 5;
    @Positive
    private int assignmentTimeoutMinutes = 15;
    @Min(0)
    private int maxReassignRetries = 3;
    @Positive
    private int weightEta = 10;
    @Positive
    private int weightWorkload = 50;
    @Positive
    private int weightRating = 5;
    @Positive
    private int locationMaxAgeSeconds = 60;
    @Positive
    private int unknownEtaMinutes = 30;
}
