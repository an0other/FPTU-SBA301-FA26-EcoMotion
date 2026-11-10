package com.fptufa26.ecomotion.booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "yearly_workflow")
@Getter
@Setter
public class YearlyWorkflow {
    @Id
    private Long bookingId;
    @Version
    private Long version;
    @Column(length = 2000)
    private String stage;
    @Column(length = 2000)
    private String returnMode;
    @Column(length = 2000)
    private String settlementNote;
    @Column(length = 2000)
    private String refundReference;
    private boolean termsAccepted;
    private int graceDays;
    private Long annualKm;
    private Long currentInitialKm;
    private Long finalKm;
    private Long returnEvidenceId;
    private Long totalKm;
    private Long settlementEvidenceId;
    private Long refundEvidenceId;
    private Long refundReviewDisputeId;
    @Column(precision = 14, scale = 2)
    private BigDecimal refundVerifiedReceived = BigDecimal.ZERO;
    private long completedKm;
    private LocalDate returnedOn;
    private LocalDate failureOn;
    @Column(precision = 14, scale = 2)
    private BigDecimal excessKmRate = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal lateDailyRate = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal roundingUnit = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal contractTotal;
    @Column(precision = 14, scale = 2)
    private BigDecimal securityDeposit;
    @Column(precision = 14, scale = 2)
    private BigDecimal rentDue = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal earlyFee = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal excessKmFee = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal otherCharges = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal rentPaid = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal depositHeld = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal depositDeduction = BigDecimal.ZERO;
    /** Giảm trừ do đổi xe rẻ hơn, tính lại từ đầu mỗi lần lập quyết toán nên không cộng dồn. */
    @Column(precision = 14, scale = 2)
    private BigDecimal vehicleChangeDiscount = BigDecimal.ZERO;

    public BigDecimal getVehicleChangeDiscount() {
        return vehicleChangeDiscount == null ? BigDecimal.ZERO : vehicleChangeDiscount;
    }
    @Column(precision = 14, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal rentRefund = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal depositRefund = BigDecimal.ZERO;
}
