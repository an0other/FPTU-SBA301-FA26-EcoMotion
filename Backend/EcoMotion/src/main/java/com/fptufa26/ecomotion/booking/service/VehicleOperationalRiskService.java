package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;

public interface VehicleOperationalRiskService {

    OperationalRiskResponse assess(Booking booking);

    OperationalRiskResponse assessForRenter(Long userId, String bookingCode);

    OperationalRiskResponse assessForProvider(Long userId, String bookingCode);

    /**
     * Throws ConflictException unless {@code vehicle} can really take {@code booking} for [start, end):
     * the previous trip must be back, no other booking may overlap and the vehicle schedule must be free.
     * Read-only; used both when a proposal is sent and when it is applied.
     */
    void requireVehicleReadyForWindow(Booking booking, com.fptufa26.ecomotion.vehicle.entity.Vehicle vehicle,
            java.time.Instant start, java.time.Instant end);
}
