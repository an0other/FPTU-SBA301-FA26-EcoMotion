package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YearlyEndAcceptanceTest {
    @Test
    void acceptsVerifiedAdminYearlyEndWithoutPretendingRenterConfirmed() {
        var booking = new Booking();
        var plan = new RentalPlan();
        plan.setPlanType(PlanType.YEARLY);
        booking.setPlan(plan);
        booking.setEndConfirmationMethod("ADMIN");
        assertFalse(booking.isEndAccepted());
        booking.setActualEndAt(Instant.parse("2026-11-12T00:00:00Z"));
        booking.setProviderConfirmedAt(Instant.parse("2026-11-12T01:00:00Z"));
        assertTrue(booking.isEndAccepted());
        assertNull(booking.getRenterEndConfirmedAt());
        plan.setPlanType(PlanType.DAILY);
        assertFalse(booking.isEndAccepted());
        booking.setEndConfirmationMethod("AUTO");
        assertTrue(booking.isEndAccepted());
    }
}
