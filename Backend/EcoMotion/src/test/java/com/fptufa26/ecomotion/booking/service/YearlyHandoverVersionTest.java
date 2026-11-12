package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.implement.YearlyHandoverServiceImpl;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YearlyHandoverVersionTest {
    @Test
    void staleReceiptIsRejectedBeforeAnyFinancialOrScheduleChange() {
        var bookings = mock(BookingRepository.class); var handovers = mock(YearlyHandoverRepository.class);
        var payments = mock(PaymentRequestService.class); var schedules = mock(ScheduleService.class);
        var vehicles = mock(VehicleService.class); var workflow = mock(YearlyWorkflowService.class);
        var service = new YearlyHandoverServiceImpl(bookings, handovers, schedules, payments, mock(TripEvidenceService.class), vehicles,
                mock(BookingNotificationService.class), Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), ZoneOffset.UTC), workflow);
        var user = new User(); user.setId(1L); var renter = new Renter(); renter.setUser(user);
        var plan = new RentalPlan(); plan.setPlanType(PlanType.YEARLY); var vehicle = new Vehicle(); vehicle.setId(5L);
        var b = new Booking(); b.setId(40L); b.setBookingCode("YEAR"); b.setPlan(plan); b.setRenter(renter); b.setVehicle(vehicle); b.setStatus(BookingStatus.CONFIRMED);
        var h = new YearlyHandover(); h.setBookingId(40L); h.setVersion(2L); h.setStage("ARRIVED"); h.setInitialKm(12000L);
        when(bookings.findByBookingCodeForUpdate("YEAR")).thenReturn(Optional.of(b)); when(handovers.findById(40L)).thenReturn(Optional.of(h));
        assertThrows(ConflictException.class, () -> service.receive(1L, "YEAR", 1L));
        verifyNoInteractions(schedules, payments, vehicles);
        assertEquals("ARRIVED", h.getStage());
        assertEquals(12000L, h.getInitialKm());
    }
}
