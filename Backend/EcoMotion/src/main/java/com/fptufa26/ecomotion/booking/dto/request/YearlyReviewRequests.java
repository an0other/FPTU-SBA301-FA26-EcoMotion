package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class YearlyReviewRequests {
    private YearlyReviewRequests() {}
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
