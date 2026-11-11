package com.fptufa26.ecomotion.finance.entity;

import com.fptufa26.ecomotion.booking.entity.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commission_statement_line",
        uniqueConstraints = @UniqueConstraint(name = "uk_commission_line_booking", columnNames = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class CommissionStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private CommissionStatement statement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal commission;
}
