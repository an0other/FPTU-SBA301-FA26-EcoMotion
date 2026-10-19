package com.fptufa26.ecomotion.finance.entity;

import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_evidence")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_request_id", nullable = false)
    private PaymentRequest paymentRequest;

    @Column(name = "proof_image_url", nullable = false, length = 500)
    private String proofImageUrl;

    @Column(name = "transferred_amount", precision = 14, scale = 2)
    private BigDecimal transferredAmount;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentEvidenceStatus status = PaymentEvidenceStatus.PENDING;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = PaymentEvidenceStatus.PENDING;
        }
    }
}
