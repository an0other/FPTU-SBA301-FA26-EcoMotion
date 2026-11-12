package com.fptufa26.ecomotion.finance.entity;

import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commission_statement",
        uniqueConstraints = @UniqueConstraint(name = "uk_commission_provider_period", columnNames = {"provider_id", "period_month"}))
@Getter
@Setter
@NoArgsConstructor
public class CommissionStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "period_month", nullable = false, length = 7)
    private String periodMonth;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "gross_revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "commission_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "booking_count", nullable = false)
    private int bookingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommissionStatementStatus status = CommissionStatementStatus.ISSUED;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "reported_note", length = 500)
    private String reportedNote;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id")
    private User confirmedBy;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "credit_applied", precision = 14, scale = 2)
    private BigDecimal creditApplied;

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("reportedAt DESC")
    private List<CommissionPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("completedAt ASC")
    private List<CommissionStatementLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public BigDecimal credit() {
        return creditApplied == null ? BigDecimal.ZERO : creditApplied;
    }

    public BigDecimal remainingAmount() {
        return commissionAmount.subtract(credit()).subtract(paidAmount).max(BigDecimal.ZERO);
    }

    public BigDecimal overpaidAmount() {
        return paidAmount.add(credit()).subtract(commissionAmount).max(BigDecimal.ZERO);
    }
}
