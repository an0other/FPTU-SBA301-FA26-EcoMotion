package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.time.Duration;
import java.time.Instant;

public final class AvailabilityPolicy {

    private AvailabilityPolicy() {
    }

    public static Window bufferedWindow(Vehicle vehicle, Instant start, Instant end) {
        int minutes = vehicle.getMaintenanceBufferMinutes() == null
                ? 0
                : Math.max(0, vehicle.getMaintenanceBufferMinutes());
        Duration buffer = Duration.ofMinutes(minutes);
        return new Window(start.minus(buffer), end.plus(buffer));
    }

    public record Window(Instant start, Instant end) {
    }
}
