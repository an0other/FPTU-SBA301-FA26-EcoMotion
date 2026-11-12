package com.fptufa26.ecomotion.booking.service;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.fptufa26.ecomotion.finance.service.FinancialAuditService;
import com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests;

public interface YearlyWorkflowService {
    View disputeRefund(Long userId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.RefundComplaint request);
    View escalateIncident(Long userId, String code, Long incidentId, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.Complaint request);
    View adjudicateRefund(Long adminId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.RefundRuling request);
    View adjudicateIncident(Long adminId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.IncidentRuling request);
    View adminView(Long adminId, String code);
    List<ReviewCase> reviewCases(Long userId, String code);
    record ReviewCase(Long id, String category, String status, String description, List<String> evidenceUrls, String resolution) {}
    View disputeSettlement(Long userId, String code, YearlyReviewRequests.Complaint request);
    View replyReview(Long userId, String code, YearlyReviewRequests.Reply request);
    View adjudicateSettlement(Long adminId, String code, YearlyReviewRequests.SettlementRuling request);
    void adjudicateCancellation(Long adminId, String code, YearlyReviewRequests.CancellationRuling request);
    java.util.List<FinancialAuditService.Entry> reviewHistory(Long userId, String code);

    Registration registration(Long userId, String code);
    record Registration(String phase, String responsibleRole, String message, boolean holdValid,
                        boolean canSetTerms, boolean canAcceptTerms, boolean canUploadContract,
                        boolean canPay, boolean canDispatch, boolean canArrive, boolean canReceive,
                        BigDecimal pickupFee, BigDecimal firstMonthRent, BigDecimal securityDeposit,
                        LocalDate receiptStart, LocalDate receiptDue, java.time.Instant holdExpiresAt, String receiptIssue) {}
    View get(Long userId, String code);
    Preview preview(Long userId, String code, PreviewRequest request);
    View terms(Long userId, String code, Terms request);
    View acceptTerms(Long userId, String code, Version request);
    Operations operations(Long userId, String code);
    record Operations(boolean canRequestReturn, boolean canEditReturn, boolean canWithdrawReturn,
                      boolean canInspectReturn, boolean canConfirmReturn, boolean canProposeSettlement,
                      boolean canAcceptSettlement, boolean canTransferRefund, boolean canConfirmRefund,
                      boolean canComplete, boolean canPayRent, boolean canPayAdjustment,
                      List<Long> reopenVehicleIds, String financialIssue, String nextRole,
                      String nextTab, String nextLabel, String nextMessage, boolean needsAction) {}
    View withdrawReturn(Long userId, String code, Version request);
    View requestReturn(Long userId, String code, Return request);
    View inspectReturn(Long userId, String code, Inspection request);
    View confirmReturn(Long userId, String code, Version request);
    ReturnAssessment returnAssessment(Long userId, String code);
    ReturnAssessment settlementPreview(Long userId, String code, Settlement request);
    View proposeSettlement(Long userId, String code, Settlement request);
    View acceptSettlement(Long userId, String code, Version request);
    View refundTransfer(Long userId, String code, RefundPayment request);
    View confirmRefund(Long userId, String code, Version request);
    View complete(Long userId, String code, Version request);
    View reopen(Long userId, String code, Reopen request);
    Incident report(Long userId, String code, Report request);
    Incident resolve(Long userId, String code, Long id, Resolution request);
    Incident incidentTransfer(Long userId, String code, Long id, Transfer request);
    Incident incidentConfirm(Long userId, String code, Long id, Version request);
    Incident rejectResolution(Long userId, String code, Long id, IncidentReply request);
    record IncidentReply(@NotNull @Min(0) Long version, @NotBlank @Size(max=1000) String note) {}
    List<ReplacementOption> replacementOptions(Long userId, String code, Long incidentId, LocalDate plannedOn);
    record VehicleInfo(Long id, String name, String licensePlate, Integer manufactureYear, Integer seatCount,
                       String transmission, String fuelType, String imageUrl) {}
    record ReplacementOption(VehicleInfo vehicle, BigDecimal annualPrice, boolean lowerQuality, String qualityNotes) {}
    Replacement replacement(Long userId, String code, Replace request);
    Replacement cancelReplacement(Long userId, String code, Long id, Version request);
    Replacement acceptReplacement(Long userId, String code, Long id, AcceptReplacement request);
    Replacement replacementInspection(Long userId, String code, Long id, Exchange request);
    Replacement confirmReplacement(Long userId, String code, Long id, Version request);

    record PreviewRequest(@NotBlank @Pattern(regexp="EARLY|STANDARD|FULL_CONTRACT") String mode, @NotNull LocalDate returnedOn) {}
    record Preview(LocalDate returnedOn, Money money, String scope) {}
    record Contract(BigDecimal totalRent, BigDecimal securityDeposit, LocalDate plannedStart,
                    LocalDate actualStart, LocalDate dueOn, Long currentVehicleId) {}
    record Version(@NotNull @Min(0) Long version) {}
    record Terms(@NotNull @Min(0) Long version, @NotNull @Min(0) Long annualKm,
                 @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal excessKmRate,
                 @Min(0) int graceDays, @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal lateDailyRate) {}
    record Return(@NotNull @Min(0) Long version, @NotBlank @Pattern(regexp="EARLY|STANDARD|FULL_CONTRACT") String mode,
                  LocalDate requestedOn) {
        public Return(Long version, String mode) { this(version, mode, null); }
    }
    record Inspection(@NotNull @Min(0) Long version, @NotNull LocalDate returnedOn,
                      @NotNull @Min(0) Long finalKm, @NotNull @Min(1) Long evidenceId) {}
    record ExtraCharge(@NotBlank @Pattern(regexp="CLEANING|MISSING_ACCESSORY|UNPAID_DAMAGE|OTHER") String category,
                       @NotBlank @Size(max=500) String description,
                       @NotNull @DecimalMin("1") @Digits(integer=12, fraction=2) BigDecimal amount,
                       @NotNull @Min(1) Long evidenceId, @Min(1) Long incidentId) {}
    record Settlement(@NotNull @Min(0) Long version,
                      @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal otherCharges,
                      @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal depositDeduction,
                      @Size(max=1000) String note, Long evidenceId,
                      @Size(max=20) List<@jakarta.validation.Valid ExtraCharge> charges,
                      @DecimalMin("0") BigDecimal expectedRentPaid, @DecimalMin("0") BigDecimal expectedDepositHeld) {
        public Settlement(Long version, BigDecimal otherCharges, BigDecimal depositDeduction, String note, Long evidenceId) {
            this(version, otherCharges, depositDeduction, note, evidenceId, null, null, null);
        }
    }
    record MileageLeg(Long vehicleId, String vehicleName, LocalDate from, LocalDate to,
                      long initialKm, long finalKm, long usedKm) {}
    record ReturnAssessment(Long version, String stage, String returnMode, LocalDate startedOn, LocalDate dueOn,
                            LocalDate returnedOn, boolean returnConfirmed, int usedMonths, int chargedMonths,
                            int allowanceMonths, long annualKm, long allowedKm, long totalKm, long excessKm,
                            BigDecimal excessKmRate, long calendarLateDays, int graceDays, long chargedLateDays,
                            BigDecimal lateDailyRate, List<MileageLeg> legs, List<ExtraCharge> charges,
                            Money money, BigDecimal maxDepositDeduction) {}
    record Transfer(@NotNull @Min(0) Long version, @NotBlank @Size(max=80) String bankReference,
                    @NotNull @Min(1) Long evidenceId) {}
    record RefundTransfer(Long version, String key, String bankReference, BigDecimal amount, BigDecimal verifiedAmount,
                          String status, Long evidenceId, java.time.Instant submittedAt, java.time.Instant verifiedAt, Long verifiedBy) {}
    record RefundPayment(@NotNull @Min(0) Long version, @NotBlank @Size(max=80) String bankReference,
                         @NotNull @Min(1) Long evidenceId, @NotNull @DecimalMin("1") @Digits(integer=12, fraction=2) BigDecimal amount) {}
    record Reopen(@NotNull @Min(0) Long version, @NotNull LocalDate availableOn, Long vehicleId) {}
    record Report(@NotBlank @Size(max=64) String requestId, @NotBlank @Pattern(regexp="OWNER_MAINTENANCE|RENTER_DAMAGE") String category,
                  @NotNull LocalDate occurredOn, @NotBlank @Size(max=1000) String description,
                  @NotNull @Min(1) Long evidenceId, @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal invoiceAmount) {}
    record Resolution(@NotNull @Min(0) Long version, @NotBlank @Pattern(regexp="FIXED|REIMBURSE|RENTER_PAID|END_PROVIDER|REPLACE") String outcome,
                      @NotBlank @Size(max=1000) String note, @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal approvedAmount,
                      @Pattern(regexp="OWNER_MAINTENANCE|RENTER_DAMAGE") String responsibility) {
        public Resolution(Long version, String outcome, String note, BigDecimal approvedAmount) {
            this(version, outcome, note, approvedAmount, null);
        }
    }
    // B1: acknowledgeLowerQuality = khách xác nhận rõ khi xe thay thế có chất lượng thấp hơn.
    record AcceptReplacement(@NotNull @Min(0) Long version, Boolean acknowledgeLowerQuality) {}
    record Replace(@NotNull @Min(1) Long incidentId, @NotNull @Min(1) Long vehicleId,
                   @NotNull LocalDate plannedOn) {}
    record Exchange(@NotNull @Min(0) Long version, @NotNull LocalDate exchangedOn,
                    @NotNull @Min(0) Long oldFinalKm, @NotNull @Min(0) Long newInitialKm,
                    @NotNull @Min(1) Long oldEvidenceId, @NotNull @Min(1) Long newEvidenceId) {}
    record Incident(Long id, Long version, String category, LocalDate occurredOn, String description,
                    Long evidenceId, BigDecimal invoiceAmount, String status, String outcome,
                    String resolutionNote, BigDecimal approvedAmount, Long transferEvidenceId, String bankReference, String reportedCategory, String proposedCategory) {}
    record Replacement(Long id, Long version, Long incidentId, Long oldVehicleId, Long newVehicleId,
                       LocalDate plannedOn, LocalDate exchangedOn, Long oldFinalKm, Long newInitialKm,
                       Long oldEvidenceId, Long newEvidenceId, String status,
                       String oldVehicleName, String newVehicleName, BigDecimal oldPlanPrice, BigDecimal newPlanPrice,
                       boolean lowerQuality, String qualityNotes, boolean qualityAcknowledged, VehicleInfo oldVehicle, VehicleInfo newVehicle) {}
    record Money(BigDecimal rentDue, BigDecimal earlyFee, BigDecimal lateFee, BigDecimal excessKmFee,
                 BigDecimal otherCharges, BigDecimal rentPaid, BigDecimal depositHeld,
                 BigDecimal rentRefund, BigDecimal depositRefund, BigDecimal balanceDue,
                 // B1: vehicleChangeDiscount = giảm trừ do đổi xe rẻ hơn (đã tính vào rentDue/balanceDue).
                 BigDecimal depositDeduction, BigDecimal vehicleChangeDiscount, String note) {}
    record View(Long version, String stage, Contract contract, boolean termsAccepted, Long annualKm, BigDecimal excessKmRate,
                int graceDays, BigDecimal lateDailyRate, BigDecimal roundingUnit, String returnMode,
                LocalDate returnedOn, LocalDate failureOn, Long finalKm, Long returnEvidenceId, Long totalKm, long completedKm, Long currentInitialKm,
                Money money, Long settlementEvidenceId, Long refundEvidenceId, String refundReference,
                List<Incident> incidents, List<Replacement> replacements,
                BigDecimal refundVerifiedReceived, BigDecimal refundRemaining, String bookingStatus, VehicleInfo currentVehicle, LocalDate requestedReturnOn,
                java.time.Instant settlementAcceptedAt, java.time.Instant completedAt, List<ExtraCharge> settlementCharges, List<RefundTransfer> refundTransfers) {}
}
