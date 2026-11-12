package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class YearlyRefundTransfer {
    @Column(name="transfer_key", nullable=false, length=80)
    private String key;
    @Column(name="bank_reference", nullable=false, length=80)
    private String bankReference;
    @Column(name="submitted_amount", nullable=false, precision=14, scale=2)
    private BigDecimal amount;
    @Column(name="verified_amount", nullable=false, precision=14, scale=2)
    private BigDecimal verifiedAmount = BigDecimal.ZERO;
    @Column(name="verification_status", nullable=false, length=30)
    private String status;
    @Column(name="evidence_id", nullable=false)
    private Long evidenceId;
    @Column(name="submitted_at", nullable=false)
    private Instant submittedAt;
    @Column(name="verified_at")
    private Instant verifiedAt;
    @Column(name="verified_by")
    private Long verifiedBy;
}
