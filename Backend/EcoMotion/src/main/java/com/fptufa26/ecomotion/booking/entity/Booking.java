package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "booking",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_code", columnNames = "booking_code")
)
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, length = 32)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "renter_id", nullable = false)
    private Renter renter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private RentalPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "driver_confirmed_at")
    private Instant driverConfirmedAt;

    @Column(name = "actual_arrived_at")
    private Instant actualArrivedAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "driver_start_confirmed_at")
    private Instant driverStartConfirmedAt;

    @Column(name = "renter_start_confirmed_at")
    private Instant renterStartConfirmedAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "driver_end_confirmed_at")
    private Instant driverEndConfirmedAt;

    @Column(name = "renter_end_confirmed_at")
    private Instant renterEndConfirmedAt;

    @Column(name = "renter_confirmed_at")
    private Instant renterConfirmedAt;

    @Column(name = "provider_confirmed_at")
    private Instant providerConfirmedAt;

    @Column(name = "driver_replacement_required", nullable = false)
    private boolean driverReplacementRequired;

    @Column(name = "driver_issue_reason", length = 500)
    private String driverIssueReason;

    @Column(name = "driver_issue_reported_at")
    private Instant driverIssueReportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_issue_reported_by")
    private User driverIssueReportedBy;

    @Column(name = "driver_issue_resolved_at")
    private Instant driverIssueResolvedAt;

    @jakarta.persistence.Version
    @Column(name = "version")
    private Long version;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_latitude", precision = 9, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 9, scale = 6)
    private BigDecimal pickupLongitude;

    @Column(name = "driver_note", length = 160)
    private String driverNote;

    @Column(name = "pickup_time", nullable = false)
    private Instant pickupTime;

    @Column(name = "passenger_count", nullable = false)
    private Integer passengerCount;

    @Column(name = "contract_url", length = 500)
    private String contractUrl;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 40)
    private CancelReason cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_resolution", length = 30)
    private OperationalResolutionType operationalResolution;

    @Column(name = "operational_resolution_reason", length = 500)
    private String operationalResolutionReason;

    @Column(name = "operational_resolved_at")
    private Instant operationalResolvedAt;

    @Column(name = "previous_vehicle_id")
    private Long previousVehicleId;

    @Column(name = "previous_pickup_time")
    private Instant previousPickupTime;

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
            status = BookingStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
