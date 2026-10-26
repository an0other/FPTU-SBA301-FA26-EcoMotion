package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverAssignmentScheduler {

    private final DriverOptimizationService driverOptimizationService;

    @Scheduled(fixedDelay = 60000)
    public void checkAssignmentTimeouts() {
        try {
            driverOptimizationService.processPendingAssignmentTimeouts();
        } catch (Exception ex) {
            log.error("Failed to process assignment timeouts: {}", ex.getMessage(), ex);
        }
    }
}
