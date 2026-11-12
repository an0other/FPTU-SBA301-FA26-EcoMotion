package com.fptufa26.ecomotion.finance.entity;

import com.fptufa26.ecomotion.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commission_payment")
@Getter
@Setter
@NoArgsConstructor
public class CommissionPayment {

    public static final String REPORTED = "REPORTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private CommissionStatement statement;

    @Column(name = "declared_amount", precision = 14, scale = 2)
    private BigDecimal declaredAmount;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(length = 500)
    private String note;

    @Column(name = "proof_path", length = 500)
    private String proofPath;

    @Column(name = "proof_content_type", length = 60)
    private String proofContentType;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(nullable = false, length = 20)
    private String status = REPORTED;

    @Column(name = "confirmed_amount", precision = 14, scale = 2)
    private BigDecimal confirmedAmount;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private User decidedBy;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    public static final String DISPUTE_OPEN = "OPEN";
    public static final String DISPUTE_ACCEPTED = "ACCEPTED";
    public static final String DISPUTE_REJECTED = "REJECTED";

    @Column(name = "dispute_status", length = 20)
    private String disputeStatus;

    @Column(name = "claimed_amount", precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "dispute_note", length = 500)
    private String disputeNote;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_resolved_at")
    private Instant disputeResolvedAt;

    @Column(name = "dispute_resolution", length = 500)
    private String disputeResolution;

    @Column(name = "dispute_proof_path", length = 500)
    private String disputeProofPath;

    @Column(name = "dispute_proof_content_type", length = 60)
    private String disputeProofContentType;
}
