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

    View get(Long userId, String code);
    Preview preview(Long userId, String code, PreviewRequest request);
    View terms(Long userId, String code, Terms request);
    View acceptTerms(Long userId, String code, Version request);
    View requestReturn(Long userId, String code, Return request);
    View inspectReturn(Long userId, String code, Inspection request);
    View confirmReturn(Long userId, String code, Version request);
    View proposeSettlement(Long userId, String code, Settlement request);
    View acceptSettlement(Long userId, String code, Version request);
    View refundTransfer(Long userId, String code, Transfer request);
    View confirmRefund(Long userId, String code, Version request);
    View complete(Long userId, String code, Version request);
    View reopen(Long userId, String code, Reopen request);
    Incident report(Long userId, String code, Report request);
    Incident resolve(Long userId, String code, Long id, Resolution request);
    Incident incidentTransfer(Long userId, String code, Long id, Transfer request);
    Incident incidentConfirm(Long userId, String code, Long id, Version request);
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
    record Return(@NotNull @Min(0) Long version, @NotBlank @Pattern(regexp="EARLY|STANDARD|FULL_CONTRACT") String mode) {}
    record Inspection(@NotNull @Min(0) Long version, @NotNull LocalDate returnedOn,
                      @NotNull @Min(0) Long finalKm, @NotNull @Min(1) Long evidenceId) {}
    record Settlement(@NotNull @Min(0) Long version,
                      @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal otherCharges,
                      @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal depositDeduction,
                      @Size(max=1000) String note, Long evidenceId) {}
    record Transfer(@NotNull @Min(0) Long version, @NotBlank @Size(max=80) String bankReference,
                    @NotNull @Min(1) Long evidenceId) {}
    record Reopen(@NotNull @Min(0) Long version, @NotNull LocalDate availableOn, Long vehicleId) {}
    record Report(@NotBlank @Size(max=64) String requestId, @NotBlank @Pattern(regexp="OWNER_MAINTENANCE|RENTER_DAMAGE") String category,
                  @NotNull LocalDate occurredOn, @NotBlank @Size(max=1000) String description,
                  @NotNull @Min(1) Long evidenceId, @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal invoiceAmount) {}
    record Resolution(@NotNull @Min(0) Long version, @NotBlank @Pattern(regexp="FIXED|REIMBURSE|RENTER_PAID|END_PROVIDER|REPLACE") String outcome,
                      @NotBlank @Size(max=1000) String note, @NotNull @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal approvedAmount) {}
    // B1: acknowledgeLowerQuality = khách xác nhận rõ khi xe thay thế có chất lượng thấp hơn.
    record AcceptReplacement(@NotNull @Min(0) Long version, Boolean acknowledgeLowerQuality) {}
    record Replace(@NotNull @Min(1) Long incidentId, @NotNull @Min(1) Long vehicleId,
                   @NotNull LocalDate plannedOn) {}
    record Exchange(@NotNull @Min(0) Long version, @NotNull LocalDate exchangedOn,
                    @NotNull @Min(0) Long oldFinalKm, @NotNull @Min(0) Long newInitialKm,
                    @NotNull @Min(1) Long oldEvidenceId, @NotNull @Min(1) Long newEvidenceId) {}
    record Incident(Long id, Long version, String category, LocalDate occurredOn, String description,
                    Long evidenceId, BigDecimal invoiceAmount, String status, String outcome,
                    String resolutionNote, BigDecimal approvedAmount, Long transferEvidenceId, String bankReference) {}
    record Replacement(Long id, Long version, Long incidentId, Long oldVehicleId, Long newVehicleId,
                       LocalDate plannedOn, LocalDate exchangedOn, Long oldFinalKm, Long newInitialKm,
                       Long oldEvidenceId, Long newEvidenceId, String status,
                       String oldVehicleName, String newVehicleName, BigDecimal oldPlanPrice, BigDecimal newPlanPrice,
                       boolean lowerQuality, String qualityNotes, boolean qualityAcknowledged) {}
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
                BigDecimal refundVerifiedReceived, BigDecimal refundRemaining) {}
}
