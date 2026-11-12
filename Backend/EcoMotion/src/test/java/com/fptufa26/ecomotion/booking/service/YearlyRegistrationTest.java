package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.implement.YearlyWorkflowServiceImpl;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YearlyRegistrationTest {
    @Test
    void acceptedTermsAndHoldDetermineWhoCanProceed() {
        var bookings = mock(BookingRepository.class);
        var workflows = mock(YearlyWorkflowRepository.class);
        var handovers = mock(YearlyHandoverRepository.class);
        var payments = mock(PaymentRequestService.class);
        var schedules = mock(ScheduleService.class);
        var ledger = mock(BookingPaymentLedgerService.class);
        var service = new YearlyWorkflowServiceImpl(bookings, workflows, mock(YearlyIncidentRepository.class),
                mock(YearlyReplacementRepository.class), handovers, mock(TripEvidenceService.class), payments,
                schedules, mock(VehicleService.class), mock(BookingNotificationService.class),
                Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), ZoneOffset.UTC), ledger,
                mock(DisputeService.class), mock(FinancialAuditService.class), mock(UserService.class),
                mock(RefundEventService.class), mock(CancellationClosureService.class),
                mock(RentalPricingService.class), new BigDecimal("100000"));
        var renterUser = new User(); renterUser.setId(1L);
        var renter = new Renter(); renter.setUser(renterUser);
        var owner = new User(); owner.setId(2L);
        var provider = new Provider(); provider.setUser(owner);
        var vehicle = new Vehicle(); vehicle.setProvider(provider);
        var plan = new RentalPlan(); plan.setPlanType(PlanType.YEARLY);
        var booking = new Booking(); booking.setId(3L); booking.setRenter(renter); booking.setVehicle(vehicle);
        booking.setPlan(plan); booking.setTotalAmount(new BigDecimal("181000000")); booking.setStatus(BookingStatus.ACCEPTED);
        var state = new YearlyWorkflow(); state.setRoundingUnit(new BigDecimal("100000"));
        var snapshot = new BookingPolicySnapshot(); snapshot.setDepositAmount(new BigDecimal("50000000"));
        snapshot.setPickupFee(new BigDecimal("200000"));
        when(bookings.findByBookingCode("DEMO")).thenReturn(Optional.of(booking));
        when(workflows.findById(3L)).thenReturn(Optional.of(state));
        when(ledger.findSnapshot(3L)).thenReturn(Optional.of(snapshot));
        when(schedules.hasUnexpiredHold(3L, Instant.parse("2026-11-12T05:00:00Z"))).thenReturn(true);
        var draft = service.registration(1L, "DEMO");
        assertEquals("TERMS_DRAFT", draft.phase());
        assertFalse(draft.canPay()); assertFalse(draft.canUploadContract());
        state.setAnnualKm(24000L);
        assertTrue(service.registration(1L, "DEMO").canAcceptTerms());
        state.setTermsAccepted(true);
        var contract = service.registration(1L, "DEMO");
        assertEquals("CONTRACT", contract.phase()); assertTrue(contract.canUploadContract());
        assertEquals(new BigDecimal("15100000"), contract.firstMonthRent());
        booking.setContractUrl("booking/1/demo.pdf"); booking.setStatus(BookingStatus.AWAITING_DEPOSIT);
        assertEquals("PAYMENT", service.registration(1L, "DEMO").phase());
        when(schedules.hasUnexpiredHold(3L, Instant.parse("2026-11-12T05:00:00Z"))).thenReturn(false);
        var expired = service.registration(1L, "DEMO");
        assertEquals("EXPIRED", expired.phase()); assertFalse(expired.canPay());
        booking.setStatus(BookingStatus.CONFIRMED);
        when(payments.yearlyInitialPaymentsComplete(3L)).thenReturn(true);
        assertTrue(service.registration(2L, "DEMO").canDispatch());
        var arrived = new YearlyHandover(); arrived.setStage("ARRIVED");
        when(handovers.findById(3L)).thenReturn(Optional.of(arrived));
        doThrow(new com.fptufa26.ecomotion.common.exception.ConflictException("Ngày nhận sớm trùng lịch khác"))
                .when(schedules).assertFreeForMove(eq(booking), eq(vehicle), any(), any());
        var blocked = service.registration(1L, "DEMO");
        assertFalse(blocked.canReceive());
        assertEquals("Ngày nhận sớm trùng lịch khác", blocked.receiptIssue());
        doNothing().when(schedules).assertFreeForMove(eq(booking), eq(vehicle), any(), any());
        assertTrue(service.registration(1L, "DEMO").canReceive());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.registration(99L, "DEMO"));
    }

    @Test
    void calendarBoundariesKeepLeapDayAndOriginalMonthAnchor() {
        assertEquals(LocalDate.of(2025,2,28), com.fptufa26.ecomotion.booking.policy.YearlyRentalRules
                .contractEnd(Instant.parse("2024-02-29T03:00:00Z")).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        assertEquals(LocalDate.of(2027,3,31), com.fptufa26.ecomotion.booking.policy.YearlyRentalRules
                .monthBoundary(LocalDate.of(2027,1,31),2));
    }
    @Test
    void pickupFeeIsExcludedFromRentOnlyWhenActuallyBilled() {
        var requests = mock(com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository.class);
        var ledger = mock(BookingPaymentLedgerService.class);
        var refunds = mock(RefundEventService.class);
        var service = mock(com.fptufa26.ecomotion.finance.service.implement.PaymentRequestServiceImpl.class, CALLS_REAL_METHODS);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "paymentRequestRepository", requests);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bookingPaymentLedgerService", ledger);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "refundEventService", refunds);
        var b = new Booking(); b.setTotalAmount(new BigDecimal("120000000"));
        var month = new com.fptufa26.ecomotion.finance.entity.PaymentRequest();
        month.setBooking(b); month.setInstallmentNumber(1); month.setAmount(new BigDecimal("10200000"));
        month.setPaidAmount(new BigDecimal("10200000"));
        var snapshot = new BookingPolicySnapshot(); snapshot.setPickupFee(new BigDecimal("200000"));
        when(requests.findByBooking_IdOrderByCreatedAtDesc(3L)).thenReturn(java.util.List.of(month));
        when(ledger.findSnapshot(3L)).thenReturn(Optional.of(snapshot));
        when(ledger.yearlyRoundingUnit(3L)).thenReturn(Optional.of(new BigDecimal("100000")));
        assertEquals(0, new BigDecimal("10000000").compareTo(service.yearlyCash(3L).rentPaid()));
        month.setAmount(new BigDecimal("10000000")); month.setPaidAmount(new BigDecimal("10000000"));
        assertEquals(0, new BigDecimal("10000000").compareTo(service.yearlyCash(3L).rentPaid()));
    }

}
