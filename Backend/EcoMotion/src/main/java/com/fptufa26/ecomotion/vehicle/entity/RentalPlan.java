package com.fptufa26.ecomotion.vehicle.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rental_plan")
@Getter
@Setter
@NoArgsConstructor
public class RentalPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    @Column(name = "slot_length_minutes", nullable = false)
    private Integer slotLengthMinutes;

    @Column(name = "slot_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal slotPrice;

    @Column(name = "upfront_amount", precision = 14, scale = 2)
    private BigDecimal upfrontAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.ACTIVE;

    @Column(name = "allowed_overtime_minutes")
    private Integer allowedOvertimeMinutes;

    @Column(name = "overtime_surcharge_ratio", precision = 6, scale = 4)
    private BigDecimal overtimeSurchargeRatio;

    @Column(name = "free_radius_km")
    private Integer freeRadiusKm;

    @Column(name = "out_of_radius_rate", precision = 14, scale = 2)
    private BigDecimal outOfRadiusRate;

    @Column(name = "waiting_free_minutes")
    private Integer waitingFreeMinutes;

    @Column(name = "waiting_rate", precision = 14, scale = 2)
    private BigDecimal waitingRate;

    @Column(name = "weekend_surcharge_ratio", precision = 6, scale = 4)
    private BigDecimal weekendSurchargeRatio;

    @Column(name = "hourly_to_daily_threshold_minutes")
    private Integer hourlyToDailyThresholdMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = PlanStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
