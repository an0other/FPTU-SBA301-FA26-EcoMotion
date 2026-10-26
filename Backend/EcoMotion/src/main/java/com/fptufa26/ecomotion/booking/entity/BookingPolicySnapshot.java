package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "booking_policy_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class BookingPolicySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "slot_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal slotPrice;

    @Column(name = "slot_length_minutes", nullable = false)
    private Integer slotLengthMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    @Column(name = "deposit_ratio", precision = 6, scale = 4)
    private BigDecimal depositRatio;

    @Column(name = "minimum_deposit_amount", precision = 14, scale = 2)
    private BigDecimal minimumDepositAmount;

    @Column(name = "deposit_amount", precision = 14, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "vehicle_name", length = 180)
    private String vehicleName;

    @Column(name = "vehicle_manufacture_year")
    private Integer vehicleManufactureYear;

    @Column(name = "vehicle_license_plate", length = 20)
    private String vehicleLicensePlate;

    @Column(name = "vehicle_image_url", length = 500)
    private String vehicleImageUrl;

    @Column(name = "upfront_amount", precision = 14, scale = 2)
    private BigDecimal upfrontAmount;

    @Column(name = "allowed_overtime_minutes")
    private Integer allowedOvertimeMinutes;

    @Column(name = "overtime_surcharge_ratio", precision = 6, scale = 4)
    private BigDecimal overtimeSurchargeRatio;

    @Column(name = "waiting_free_minutes")
    private Integer waitingFreeMinutes;

    @Column(name = "waiting_rate", precision = 14, scale = 2)
    private BigDecimal waitingRate;

    @Column(name = "weekend_surcharge_ratio", precision = 6, scale = 4)
    private BigDecimal weekendSurchargeRatio;

    @Column(name = "free_radius_km")
    private Integer freeRadiusKm;

    @Column(name = "out_of_radius_rate", precision = 14, scale = 2)
    private BigDecimal outOfRadiusRate;

    @Column(name = "hourly_to_daily_threshold_minutes")
    private Integer hourlyToDailyThresholdMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
