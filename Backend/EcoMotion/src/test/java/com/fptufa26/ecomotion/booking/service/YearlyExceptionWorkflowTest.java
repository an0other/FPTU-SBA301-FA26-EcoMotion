package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.*;
import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.implement.YearlyWorkflowServiceImpl;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.*;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class YearlyExceptionWorkflowTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final YearlyWorkflowRepository workflows = mock(YearlyWorkflowRepository.class);
    private final YearlyIncidentRepository incidents = mock(YearlyIncidentRepository.class);
    private final YearlyReplacementRepository replacements = mock(YearlyReplacementRepository.class);
    private final DisputeService disputes = mock(DisputeService.class);
    private final FinancialAuditService audit = mock(FinancialAuditService.class);
    private final BookingPaymentLedgerService ledger = mock(BookingPaymentLedgerService.class);
    private final UserService users = mock(UserService.class);
    private final PaymentRequestService payments = mock(PaymentRequestService.class);
    private final TripEvidenceService evidence = mock(TripEvidenceService.class);
    private YearlyWorkflowServiceImpl service;
    private Booking booking;
    private YearlyWorkflow state;
    private Dispute dispute;

    @BeforeEach
    void setup() {
        var clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
        service = new YearlyWorkflowServiceImpl(bookings, workflows, incidents, replacements,
                mock(YearlyHandoverRepository.class), evidence, payments, mock(ScheduleService.class),
                mock(VehicleService.class), mock(BookingNotificationService.class), clock, ledger, disputes,
                audit, users, mock(RefundEventService.class), mock(CancellationClosureService.class), mock(RentalPricingService.class), BigDecimal.valueOf(100000));
        var providerUser = new User(); providerUser.setId(2L);
        var provider = new Provider(); provider.setUser(providerUser);
        var vehicle = new Vehicle(); vehicle.setId(5L); vehicle.setProvider(provider);
        var renterUser = new User(); renterUser.setId(1L);
        var renter = new Renter(); renter.setUser(renterUser);
        var plan = new RentalPlan(); plan.setPlanType(PlanType.YEARLY);
        booking = new Booking(); booking.setId(40L); booking.setBookingCode("YEAR-TEST");
        booking.setRenter(renter); booking.setVehicle(vehicle); booking.setPlan(plan);
        booking.setPickupTime(clock.instant().minus(Duration.ofDays(365)));
        booking.setPlannedEndAt(clock.instant()); booking.setActualStartAt(booking.getPickupTime());
        booking.setActualEndAt(clock.instant()); booking.setTotalAmount(BigDecimal.valueOf(120000000));
        state = new YearlyWorkflow(); state.setBookingId(40L); state.setVersion(1L);
        state.setStage("REFUND_TRANSFER_RECORDED"); state.setCurrentInitialKm(0L);
        state.setRentRefund(BigDecimal.TEN); state.setDepositRefund(BigDecimal.valueOf(20));
        state.setRefundReviewDisputeId(77L);
        when(bookings.findByBookingCode("YEAR-TEST")).thenReturn(Optional.of(booking));
        when(bookings.findByBookingCodeForUpdate("YEAR-TEST")).thenReturn(Optional.of(booking));
        when(ledger.requireBookingForUpdate(40L)).thenReturn(booking);
        when(workflows.findById(40L)).thenReturn(Optional.of(state));
        var admin = new User(); admin.setId(3L); admin.setRole(Role.ADMIN); admin.setStatus(UserStatus.ACTIVE);
        when(users.findById(3L)).thenReturn(Optional.of(admin));
        dispute = new Dispute(); dispute.setId(77L); dispute.setBooking(booking);
        dispute.setCategory(DisputeCategory.YEARLY_REFUND); dispute.setStatus(DisputeStatus.OPEN);
        when(disputes.getDisputeById(77L)).thenReturn(Optional.of(dispute));
    }

    private RefundRuling ruling(String outcome, long received) {
        return new RefundRuling("request-1", 1L, 77L, outcome, BigDecimal.valueOf(received), "Verified statement", "BANK-REF");
    }

    @Test
    void holdKeepsRefundFrozenAndDoesNotRecordPayment() {
        service.adjudicateRefund(3L, "YEAR-TEST", ruling("HOLD", 0));
        assertEquals("REFUND_TRANSFER_RECORDED", state.getStage());
        verifyNoInteractions(payments);
        verify(disputes, never()).resolveYearlyReview(anyLong(), anyLong(), anyString());
    }

    @Test
    void partialReceiptOnlyReopensRemainingAmount() {
        var result = service.adjudicateRefund(3L, "YEAR-TEST", ruling("PARTIAL_RECEIVED", 12));
        assertEquals("SETTLEMENT_ACCEPTED", state.getStage());
        assertEquals(BigDecimal.valueOf(18), result.refundRemaining());
        assertEquals(BigDecimal.valueOf(12), result.refundVerifiedReceived());
        verifyNoInteractions(payments);
        verify(disputes).resolveYearlyReview(eq(77L), eq(3L), anyString());
        verify(audit).record(eq("YEARLY"), eq(40L), eq("request-1"), eq(3L), eq("REFUND_PARTIAL_RECEIVED"), anyString(), startsWith("REFUND_TRANSFER_RECORDED:"));
    }

    @Test
    void cannotDeclareFullReceiptForPartialAmount() {
        assertThrows(ConflictException.class, () -> service.adjudicateRefund(3L, "YEAR-TEST", ruling("CONFIRMED_RECEIVED", 12)));
        verifyNoInteractions(payments);
    }

    @Test
    void fullReceiptIsRecordedAsAdminReconciliation() {
        state.setRefundEvidenceId(9L); state.setRefundReference("BANK-OLD");
        var image = new com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse(9L, 40L, "YEAR-TEST",
                com.fptufa26.ecomotion.booking.enums.TripEvidenceType.OTHER, "booking/proof", null, null, null, null, null, null, null, Instant.now());
        when(evidence.listEvidence(2L, Role.PROVIDER, "YEAR-TEST")).thenReturn(List.of(image));
        service.adjudicateRefund(3L, "YEAR-TEST", ruling("CONFIRMED_RECEIVED", 30));
        assertEquals("REFUND_RECEIVED", state.getStage());
        assertEquals(BigDecimal.valueOf(30), state.getRefundVerifiedReceived());
        verify(payments).recordYearlyRefundByAdmin(40L, BigDecimal.TEN, BigDecimal.valueOf(20), "BANK-OLD", "booking/proof", 3L);
        verify(payments, never()).recordYearlyRefund(anyLong(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void adminCanCloseUnresolvedReportAndUnblockWorkflow() {
        dispute.setCategory(DisputeCategory.YEARLY_INCIDENT);
        var incident = new YearlyIncident(); incident.setId(8L); incident.setBookingId(40L);
        incident.setReviewDisputeId(77L); incident.setStatus("OPEN"); incident.setCategory("OWNER_MAINTENANCE");
        when(incidents.findByIdAndBookingId(8L, 40L)).thenReturn(Optional.of(incident));
        service.adjudicateIncident(3L, "YEAR-TEST", new IncidentRuling("incident-1", 1L, 77L, 8L,
                "FIXED", BigDecimal.ZERO, "Verified repair", "PHOTO-1"));
        assertEquals("CLOSED", incident.getStatus());
        verify(disputes).resolveYearlyReview(77L, 3L, "Verified repair");
        verifyNoInteractions(payments);
    }

    @Test
    void cannotDecreaseVerifiedReceiptOrExceedTotal() {
        state.setRefundVerifiedReceived(BigDecimal.TEN);
        assertThrows(ConflictException.class, () -> service.adjudicateRefund(3L, "YEAR-TEST", ruling("FAILED_VERIFIED", 0)));
        assertThrows(ConflictException.class, () -> service.adjudicateRefund(3L, "YEAR-TEST", ruling("PARTIAL_RECEIVED", 31)));
    }

    @Test
    void unrelatedDisputeCannotChangeRefund() {
        dispute.setCategory(DisputeCategory.YEARLY_SETTLEMENT);
        assertThrows(ConflictException.class, () -> service.adjudicateRefund(3L, "YEAR-TEST", ruling("FAILED_VERIFIED", 0)));
        assertEquals("REFUND_TRANSFER_RECORDED", state.getStage());
    }

    @Test
    void agreedReimbursementDoesNotBlockPhysicalReturn() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        var incident = new YearlyIncident(); incident.setStatus("AWAITING_TRANSFER");
        when(incidents.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(incident));
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        service.requestReturn(1L, "YEAR-TEST", new YearlyWorkflowService.Return(1L, "EARLY"));
        assertEquals("RETURN_REQUESTED", state.getStage());
    }

    @Test
    void openReportOrAdminReviewStillBlocksUnilateralReturn() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        var incident = new YearlyIncident(); incident.setStatus("OPEN");
        when(incidents.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(incident));
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.requestReturn(1L, "YEAR-TEST", new YearlyWorkflowService.Return(1L, "EARLY")));
        incident.setStatus("CLOSED"); when(disputes.hasOpenDispute(40L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.requestReturn(1L, "YEAR-TEST", new YearlyWorkflowService.Return(1L, "EARLY")));
    }
}
