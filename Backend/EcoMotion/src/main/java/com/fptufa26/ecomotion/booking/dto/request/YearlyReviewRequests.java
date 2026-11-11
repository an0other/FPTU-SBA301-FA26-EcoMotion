package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class YearlyReviewRequests {
    private YearlyReviewRequests() {}
    public record RefundComplaint(@NotBlank @Size(max=80) String requestId, @NotNull @Min(0) Long version,
            @NotBlank @Size(max=1000) String note, @NotNull @Min(1) Long evidenceId,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal receivedAmount) {}
    public record RefundRuling(@NotBlank @Size(max=80) String requestId, @NotNull @Min(0) Long version,
            @NotNull @Min(1) Long disputeId, @NotBlank @Pattern(regexp="HOLD|FAILED_VERIFIED|PARTIAL_RECEIVED|CONFIRMED_RECEIVED") String outcome,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal receivedAmount,
            @NotBlank @Size(max=1000) String note, @NotBlank @Size(max=500) String evidenceReference) {}
    public record IncidentRuling(@NotBlank @Size(max=80) String requestId, @NotNull @Min(0) Long version,
            @NotNull @Min(1) Long disputeId, @NotNull @Min(1) Long incidentId,
            @NotBlank @Pattern(regexp="HOLD|FIXED|RENTER_PAID|REIMBURSE|END_PROVIDER|TRANSFER_VERIFIED|FAILED_TRANSFER") String outcome,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal approvedAmount,
            @NotBlank @Size(max=1000) String note, @NotBlank @Size(max=500) String evidenceReference) {}
    public record Complaint(@NotBlank @Size(max=80) String requestId, @NotNull @Min(0) Long version,
                            @NotBlank @Size(max=1000) String note, @NotNull @Min(1) Long evidenceId) {}
    public record Reply(@NotBlank @Size(max=80) String requestId, @NotNull @Min(1) Long disputeId,
                        @NotBlank @Size(max=1000) String note, @NotNull @Min(1) Long evidenceId) {}
    public record SettlementRuling(@NotBlank @Size(max=80) String requestId, @NotNull @Min(0) Long version,
            @NotNull @Min(1) Long disputeId, @NotNull LocalDate returnedOn, @NotNull @Min(0) Long finalKm,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal otherCharges,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal depositDeduction,
            @NotBlank @Size(max=1000) String note, @NotNull @Min(1) Long evidenceId) {}
    public record CancellationRuling(@NotBlank @Size(max=80) String requestId, @NotNull @Min(1) Long disputeId,
            @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal refundAmount,
            @NotBlank @Size(max=1000) String note, @NotBlank @Size(max=500) String evidenceReference) {}
}
