package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityPolicyTest {

    @Test
    void usesTheVehicleMaintenanceBufferForBothSidesOfTheRental() {
        Vehicle vehicle = new Vehicle();
        vehicle.setMaintenanceBufferMinutes(45);
        Instant start = Instant.parse("2026-11-01T01:00:00Z");
        Instant end = Instant.parse("2026-11-01T05:00:00Z");

        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(vehicle, start, end);

        assertThat(window.start()).isEqualTo(Instant.parse("2026-11-01T00:15:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-11-01T05:45:00Z"));
    }

    @Test
    void treatsAMissingBufferAsZeroMinutes() {
        Vehicle vehicle = new Vehicle();
        Instant start = Instant.parse("2026-11-01T01:00:00Z");
        Instant end = Instant.parse("2026-11-01T05:00:00Z");

        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(vehicle, start, end);

        assertThat(window.start()).isEqualTo(start);
        assertThat(window.end()).isEqualTo(end);
    }
}
