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
    private final YearlyHandoverRepository handovers = mock(YearlyHandoverRepository.class);
    private final VehicleService vehicles = mock(VehicleService.class);
    private final ScheduleService schedules = mock(ScheduleService.class);
    private final RefundEventService refundEvents = mock(RefundEventService.class);
    private final RentalPricingService pricing = mock(RentalPricingService.class);
    private YearlyWorkflowServiceImpl service;
    private Booking booking;
    private YearlyWorkflow state;
    private Dispute dispute;

    @BeforeEach
    void setup() {
        var clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
        service = new YearlyWorkflowServiceImpl(bookings, workflows, incidents, replacements,
                handovers, evidence, payments, schedules,
                vehicles, mock(BookingNotificationService.class), clock, ledger, disputes,
                audit, users, refundEvents, mock(CancellationClosureService.class), pricing, BigDecimal.valueOf(100000));
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
        state.setRefundReviewDisputeId(77L); state.setRefundEvidenceId(9L); state.setRefundReference("BANK-OLD");
        var refundImage = new com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse(9L, 40L, "YEAR-TEST",
                com.fptufa26.ecomotion.booking.enums.TripEvidenceType.OTHER, "booking/proof", null, null, null, null, null, null, null, clock.instant());
        when(evidence.listEvidence(2L, Role.PROVIDER, "YEAR-TEST")).thenReturn(List.of(refundImage));
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
        verify(payments).recordYearlyRefundPart(eq(40L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(2)), eq("BANK-OLD"), eq("booking/proof"), eq(3L), eq(true), anyString());
        assertEquals("PARTIAL_FINAL", state.getRefundTransfers().getFirst().getStatus());
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
        verify(payments).recordYearlyRefundPart(eq(40L), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(20)), eq("BANK-OLD"), eq("booking/proof"), eq(3L), eq(true), anyString());
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
    @Test
    void standardReturnBeforeDueIsRejectedBeforeMovingWorkflow() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setPlannedEndAt(Instant.parse("2026-12-10T00:00:00Z"));
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.requestReturn(1L, "YEAR-TEST", new YearlyWorkflowService.Return(1L, "STANDARD")));
        assertEquals("TERMS_ACCEPTED", state.getStage());
        assertNull(booking.getActualEndAt());
    }

    @Test
    void standardReturnOnDueStartsReturnWithoutClosingContract() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        var result = service.requestReturn(1L, "YEAR-TEST", new YearlyWorkflowService.Return(1L, "STANDARD"));
        assertEquals("RETURN_REQUESTED", result.stage());
        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        assertNull(booking.getActualEndAt());
        verify(payments, never()).finalizeYearlyObligations(anyLong(), any());
    }

    @Test
    void replacementOptionsExcludeBusyAndUnpricedVehicles() {
        booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setPlannedEndAt(Instant.parse("2027-11-10T00:00:00Z"));
        booking.getVehicle().getProvider().setId(22L);
        var renter = booking.getRenter();
        renter.setLicenseVerified(true); renter.setLicenseClass("B2");
        renter.setLicenseExpiry(LocalDate.of(2035,1,1)); renter.setLicenseDateOfBirth(LocalDate.of(1990,1,1));
        var i = new YearlyIncident(); i.setOutcome("REPLACE"); i.setStatus("AWAITING_CONFIRMATION");
        when(incidents.findByIdAndBookingId(7L,40L)).thenReturn(Optional.of(i));
        var free = new Vehicle(); free.setId(6L); free.setBrand("VinFast"); free.setModel("Demo");
        free.setSeatCount(5); free.setLicensePlate("51Y-00009");
        var busy = new Vehicle(); busy.setId(8L);
        var noPlan = new Vehicle(); noPlan.setId(9L); noPlan.setSeatCount(5);
        when(vehicles.findApprovedProviderVehicles(22L)).thenReturn(List.of(booking.getVehicle(),busy,free,noPlan));
        when(schedules.isRangeFree(eq(6L),any(),any())).thenReturn(true);
        when(schedules.isRangeFree(eq(9L),any(),any())).thenReturn(true);
        when(pricing.yearlyPlanPrice(6L)).thenReturn(new BigDecimal("181000000"));
        when(pricing.yearlyPlanPrice(9L)).thenThrow(new ConflictException("Không có gói năm"));
        var options = service.replacementOptions(2L,"YEAR-TEST",7L,LocalDate.of(2026,11,10));
        assertEquals(1,options.size());
        assertEquals(6L,options.get(0).vehicle().id());
        assertEquals("51Y-00009",options.get(0).vehicle().licensePlate());
        verify(pricing,never()).yearlyPlanPrice(8L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.replacementOptions(1L,"YEAR-TEST",7L,LocalDate.of(2026,11,10)));
    }

    @Test
    void reimbursementClosesOnlyAfterTransferIsRecordedAndRenterConfirms() {
        var report = new YearlyIncident(); report.setVersion(2L);
        report.setOutcome("REIMBURSE"); report.setStatus("AWAITING_TRANSFER");
        when(incidents.findByIdAndBookingId(7L,40L)).thenReturn(Optional.of(report));
        assertThrows(ConflictException.class, () -> service.incidentConfirm(1L,"YEAR-TEST",7L,new YearlyWorkflowService.Version(2L)));
        assertEquals("AWAITING_TRANSFER",report.getStatus());
        report.setStatus("TRANSFER_RECORDED");
        service.incidentConfirm(1L,"YEAR-TEST",7L,new YearlyWorkflowService.Version(2L));
        assertEquals("CLOSED",report.getStatus());
        verify(payments,never()).finalizeYearlyObligations(anyLong(),any());
    }

    private YearlyReplacement auditedReturn() {
        state.setStage("RETURNED"); state.setReturnMode("STANDARD");
        state.setRoundingUnit(new BigDecimal("100000")); state.setAnnualKm(24000L);
        state.setExcessKmRate(new BigDecimal("5000")); state.setGraceDays(1);
        state.setLateDailyRate(new BigDecimal("500000"));
        state.setReturnedOn(LocalDate.of(2026,11,10));
        state.setCurrentInitialKm(50000L); state.setCompletedKm(300L);
        state.setFinalKm(50190L); state.setTotalKm(490L);
        var h = new YearlyHandover(); h.setStage("RECEIVED"); h.setInitialKm(10000L);
        when(handovers.findById(40L)).thenReturn(Optional.of(h));
        var x = new YearlyReplacement(); x.setId(1L); x.setStatus("COMPLETED");
        x.setOldVehicleId(7L); x.setNewVehicleId(5L); x.setExchangedOn(LocalDate.of(2026,10,10));
        x.setOldFinalKm(10300L); x.setNewInitialKm(50000L);
        when(replacements.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(x));
        when(payments.yearlyCash(40L)).thenReturn(new PaymentRequestService.YearlyCash(
                new BigDecimal("120000000"), new BigDecimal("50000000")));
        return x;
    }

    @Test
    void mileageUsesDifferencesWithinEachVehicleAcrossMultipleReplacements() {
        var first = auditedReturn();
        first.setNewVehicleId(8L);
        var second = new YearlyReplacement(); second.setId(2L); second.setStatus("COMPLETED");
        second.setOldVehicleId(8L); second.setNewVehicleId(5L); second.setExchangedOn(LocalDate.of(2026,11,1));
        second.setOldFinalKm(50190L); second.setNewInitialKm(90000L);
        when(replacements.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(first,second));
        state.setCurrentInitialKm(90000L); state.setCompletedKm(490L); state.setFinalKm(90100L); state.setTotalKm(590L);
        var result = service.returnAssessment(1L,"YEAR-TEST");
        assertEquals(3,result.legs().size());
        assertEquals(300L,result.legs().get(0).usedKm());
        assertEquals(190L,result.legs().get(1).usedKm());
        assertEquals(100L,result.legs().get(2).usedKm());
        assertEquals(590L,result.totalKm());
        assertEquals(0L,result.excessKm());
        assertEquals(24000L,result.allowedKm());
        state.setCompletedKm(491L);
        assertThrows(ConflictException.class, () -> service.returnAssessment(1L,"YEAR-TEST"));
    }

    @Test
    void fullContractKeepsAnnualAllowanceAndReturnCannotPrecedeLastExchange() {
        var exchange = auditedReturn();
        state.setReturnMode("FULL_CONTRACT");
        exchange.setExchangedOn(LocalDate.of(2026,1,10));
        state.setReturnedOn(LocalDate.of(2026,2,10));
        var result = service.returnAssessment(1L,"YEAR-TEST");
        assertEquals(4,result.usedMonths());
        assertEquals(12,result.chargedMonths());
        assertEquals(24000L,result.allowedKm());
        state.setReturnedOn(LocalDate.of(2026,1,9));
        assertThrows(ConflictException.class, () -> service.returnAssessment(1L,"YEAR-TEST"));
    }

    @Test
    void automaticFeesShowExcessMileageAndLateDaysWithGrace() {
        auditedReturn();
        booking.setPlannedEndAt(Instant.parse("2026-11-06T00:00:00Z"));
        state.setFinalKm(75000L); state.setTotalKm(25300L);
        var result = service.returnAssessment(1L,"YEAR-TEST");
        assertEquals(1300L,result.excessKm());
        assertEquals(0,new BigDecimal("6500000").compareTo(result.money().excessKmFee()));
        assertEquals(4L,result.calendarLateDays());
        assertEquals(3L,result.chargedLateDays());
        assertEquals(0,new BigDecimal("1500000").compareTo(result.money().lateFee()));
        assertEquals(0,new BigDecimal("8000000").compareTo(result.money().balanceDue()));
    }

    @Test
    void confirmingReplacementAddsOldLegExactlyOnceAndStartsNewOdometer() {
        var first = auditedReturn();
        booking.setStatus(BookingStatus.IN_PROGRESS); booking.setActualEndAt(null);
        state.setStage("TERMS_ACCEPTED");
        booking.getVehicle().getProvider().setId(22L);
        var renter = booking.getRenter(); renter.setLicenseVerified(true); renter.setLicenseClass("B2");
        renter.setLicenseExpiry(LocalDate.of(2035,1,1)); renter.setLicenseDateOfBirth(LocalDate.of(1990,1,1));
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        var next = new Vehicle(); next.setId(6L); next.setProvider(booking.getVehicle().getProvider());
        next.setStatus(com.fptufa26.ecomotion.vehicle.enums.VehicleStatus.APPROVED); next.setSeatCount(5);
        var second = new YearlyReplacement(); second.setId(2L); second.setVersion(1L); second.setIncidentId(7L);
        second.setStatus("HANDOVER_PROPOSED"); second.setOldVehicleId(5L); second.setNewVehicleId(6L);
        second.setExchangedOn(LocalDate.of(2026,11,10)); second.setOldFinalKm(50190L); second.setNewInitialKm(90000L);
        var report = new YearlyIncident(); report.setStatus("AWAITING_CONFIRMATION");
        when(replacements.findByIdAndBookingId(2L,40L)).thenReturn(Optional.of(second));
        when(replacements.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(first,second));
        when(incidents.findByIdAndBookingId(7L,40L)).thenReturn(Optional.of(report));
        when(vehicles.findVehicleByIdForUpdate(5L)).thenReturn(Optional.of(booking.getVehicle()));
        when(vehicles.findVehicleByIdForUpdate(6L)).thenReturn(Optional.of(next));
        when(vehicles.findVehicleById(6L)).thenReturn(Optional.of(next));
        service.confirmReplacement(1L,"YEAR-TEST",2L,new YearlyWorkflowService.Version(1L));
        assertEquals(490L,state.getCompletedKm());
        assertEquals(90000L,state.getCurrentInitialKm());
        assertEquals(6L,booking.getVehicle().getId());
        assertEquals("CLOSED",report.getStatus());
        assertThrows(ConflictException.class, () -> service.confirmReplacement(1L,"YEAR-TEST",2L,new YearlyWorkflowService.Version(1L)));
        assertEquals(490L,state.getCompletedKm());
        verify(schedules,times(1)).moveBooking(eq(booking),eq(next),any(),any());
    }

    @Test
    void physicalReturnAndActionRemainAvailableWhenCashNeedsReconciliation() {
        auditedReturn();
        state.setStage("RETURN_INSPECTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        when(payments.yearlyCash(40L)).thenThrow(new com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException("Còn tiền chờ đối soát"));
        when(vehicles.findVehicleByIdForUpdate(5L)).thenReturn(Optional.of(booking.getVehicle()));
        var actions = service.operations(1L,"YEAR-TEST");
        assertTrue(actions.canConfirmReturn());
        assertNotNull(actions.financialIssue());
        service.confirmReturn(1L,"YEAR-TEST",new YearlyWorkflowService.Version(1L));
        assertEquals("RETURNED",state.getStage());
        assertNotNull(booking.getActualEndAt());
        assertEquals(BookingStatus.AWAITING_FINAL_PAYMENT,booking.getStatus());
        assertFalse(service.operations(2L,"YEAR-TEST").canProposeSettlement());
    }

    @Test
    void renterCanChangeAndWithdrawReturnOnlyBeforeInspection() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        var date = LocalDate.of(2026,11,12);
        service.requestReturn(1L,"YEAR-TEST",new YearlyWorkflowService.Return(1L,"STANDARD",date));
        assertEquals(date,state.getRequestedReturnOn());
        service.requestReturn(1L,"YEAR-TEST",new YearlyWorkflowService.Return(1L,"FULL_CONTRACT",date.plusDays(1)));
        assertEquals("FULL_CONTRACT",state.getReturnMode());
        service.withdrawReturn(1L,"YEAR-TEST",new YearlyWorkflowService.Version(1L));
        assertEquals("TERMS_ACCEPTED",state.getStage());
        assertNull(state.getRequestedReturnOn()); assertNull(state.getReturnMode());
        state.setStage("RETURN_INSPECTED");
        assertThrows(ConflictException.class,() -> service.withdrawReturn(1L,"YEAR-TEST",new YearlyWorkflowService.Version(1L)));
    }

    @Test
    void providerResponsibilityProposalNeedsAgreementBeforeEndingRental() {
        state.setStage("TERMS_ACCEPTED"); booking.setActualEndAt(null); booking.setStatus(BookingStatus.IN_PROGRESS);
        when(payments.yearlyInitialPaymentsComplete(40L)).thenReturn(true);
        var i = new YearlyIncident(); i.setId(7L); i.setVersion(1L); i.setStatus("OPEN");
        i.setCategory("RENTER_DAMAGE"); i.setReportedCategory("RENTER_DAMAGE"); i.setOccurredOn(LocalDate.of(2026,11,9));
        when(incidents.findByIdAndBookingId(7L,40L)).thenReturn(Optional.of(i));
        var proposal = new YearlyWorkflowService.Resolution(1L,"END_PROVIDER","Provider xác nhận lỗi xe",BigDecimal.ZERO,"OWNER_MAINTENANCE");
        service.resolve(2L,"YEAR-TEST",7L,proposal);
        assertEquals("RESOLUTION_PROPOSED",i.getStatus());
        assertEquals("RENTER_DAMAGE",i.getCategory());
        assertEquals("TERMS_ACCEPTED",state.getStage());
        service.rejectResolution(1L,"YEAR-TEST",7L,new YearlyWorkflowService.IncidentReply(1L,"Cần làm rõ hoàn tiền"));
        assertEquals("OPEN",i.getStatus()); assertNull(i.getOutcome());
        service.resolve(2L,"YEAR-TEST",7L,proposal);
        service.incidentConfirm(1L,"YEAR-TEST",7L,new YearlyWorkflowService.Version(1L));
        assertEquals("OWNER_MAINTENANCE",i.getCategory());
        assertEquals("RENTER_DAMAGE",i.getReportedCategory());
        assertEquals("CLOSED",i.getStatus());
        assertEquals("PROVIDER_FAILURE",state.getReturnMode());
        assertEquals("RETURN_REQUESTED",state.getStage());
        assertNull(booking.getActualEndAt());
    }

    @Test
    void settlementRequiresExplicitItemizedChargesAndCurrentCashBeforeAcceptance() {
        auditedReturn();
        assertThrows(ConflictException.class,() -> service.proposeSettlement(2L,"YEAR-TEST",
                new YearlyWorkflowService.Settlement(1L,BigDecimal.ONE,BigDecimal.ZERO,"Phụ phí tổng",null)));
        state.setStage("SETTLEMENT_PROPOSED"); state.setRentPaid(BigDecimal.ZERO);
        var actions = service.operations(1L,"YEAR-TEST");
        assertFalse(actions.canAcceptSettlement());
        assertEquals("PROVIDER",actions.nextRole());
        assertThrows(ConflictException.class,() -> service.acceptSettlement(1L,"YEAR-TEST",new YearlyWorkflowService.Version(1L)));
    }

    @Test
    void waitingStandaloneRefundBlocksYearlyCompletionAndDepositTransfer() {
        state.setStage("SETTLEMENT_ACCEPTED");
        when(refundEvents.hasUnsettledYearlyRefunds(40L)).thenReturn(true);
        assertThrows(ConflictException.class,() -> service.complete(2L,"YEAR-TEST",new YearlyWorkflowService.Version(1L)));
        assertThrows(ConflictException.class,() -> service.refundTransfer(2L,"YEAR-TEST",new YearlyWorkflowService.RefundPayment(1L,"REF",9L,BigDecimal.ONE)));
        var ops = service.operations(2L,"YEAR-TEST");
        assertFalse(ops.canComplete()); assertFalse(ops.canTransferRefund());
        assertEquals("payments",ops.nextTab());
        verify(payments,never()).recordYearlyRefund(anyLong(),any(),any(),any(),any(),anyLong());
    }

    @Test
    void adminCanChangeResponsibilityWithRecordedEvidence() {
        dispute.setCategory(DisputeCategory.YEARLY_INCIDENT);
        var i = new YearlyIncident(); i.setId(8L); i.setBookingId(40L); i.setReviewDisputeId(77L);
        i.setStatus("OPEN"); i.setCategory("RENTER_DAMAGE"); i.setProposedCategory("RENTER_DAMAGE"); i.setReportedCategory("OWNER_MAINTENANCE"); i.setInvoiceAmount(BigDecimal.TEN);
        when(incidents.findByIdAndBookingId(8L, 40L)).thenReturn(Optional.of(i));
        service.adjudicateIncident(3L, "YEAR-TEST", new IncidentRuling("independent", 1L, 77L, 8L,
                "REIMBURSE", BigDecimal.TEN, "Wear verified by invoice", "PHOTO-1", "OWNER_MAINTENANCE"));
        assertEquals("OWNER_MAINTENANCE", i.getCategory()); assertEquals("RENTER_DAMAGE", i.getProposedCategory());
        assertEquals("AWAITING_TRANSFER", i.getStatus());
    }

    @Test
    void refundEachTransferRecordsOnlyItsOwnAmountAndPreservesHistory() {
        service.adjudicateRefund(3L, "YEAR-TEST", ruling("PARTIAL_RECEIVED", 12));
        when(payments.yearlyBalancePaid(eq(40L), any())).thenReturn(true);
        service.refundTransfer(2L, "YEAR-TEST", new YearlyWorkflowService.RefundPayment(1L, "BANK-SECOND", 9L, BigDecimal.valueOf(18)));
        service.confirmRefund(1L, "YEAR-TEST", new YearlyWorkflowService.Version(1L));
        assertEquals("REFUND_RECEIVED", state.getStage());
        assertEquals(2, state.getRefundTransfers().size());
        assertEquals("BANK-OLD", state.getRefundTransfers().getFirst().getBankReference());
        assertEquals(BigDecimal.valueOf(12), state.getRefundTransfers().getFirst().getVerifiedAmount());
        verify(payments).recordYearlyRefundPart(eq(40L), eq(BigDecimal.ZERO), eq(BigDecimal.valueOf(18)), eq("BANK-SECOND"), eq("booking/proof"), eq(1L), eq(false), anyString());
        assertThrows(ConflictException.class, () -> service.confirmRefund(1L, "YEAR-TEST", new YearlyWorkflowService.Version(1L)));
    }

    @Test
    void adminSettlementCannotRechargeAnInvoiceAlreadyPaidByRenter() {
        dispute.setCategory(DisputeCategory.YEARLY_SETTLEMENT); state.setStage("RETURN_INSPECTED");
        var i = new YearlyIncident(); i.setEvidenceId(9L); i.setOutcome("RENTER_PAID");
        when(incidents.findByBookingIdOrderByIdAsc(40L)).thenReturn(List.of(i));
        var charge = new YearlyWorkflowService.ExtraCharge("OTHER", "Same invoice", BigDecimal.ONE, 9L, null);
        var ruling = new SettlementRuling("fees", 1L, 77L, LocalDate.of(2026,11,10), 100L, BigDecimal.ONE,
                BigDecimal.ZERO, "Verified", 9L, List.of(charge));
        var error = assertThrows(ConflictException.class, () -> service.adjudicateSettlement(3L,"YEAR-TEST",ruling));
        assertTrue(error.getMessage().contains("đã trả"));
        verify(payments, never()).finalizeYearlyObligations(anyLong(), any());
    }

}
