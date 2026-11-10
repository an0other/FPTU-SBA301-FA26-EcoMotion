package com.fptufa26.ecomotion.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "financial_review_record", uniqueConstraints = @UniqueConstraint(name = "uk_financial_review_request", columnNames = {"target_type", "target_id", "request_id"}))
@Getter
@Setter
public class FinancialReviewRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "target_type", nullable = false, length = 40)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false, length = 80)
    private String outcome;
    @Column(nullable = false, length = 4000)
    private String payload;
    @Column(nullable = false, length = 4000)
    private String previousState;
    @Column(nullable = false)
    private Instant createdAt;
}
