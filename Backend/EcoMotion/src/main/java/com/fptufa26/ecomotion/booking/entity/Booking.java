package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
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
import com.fptufa26.ecomotion.booking.enums.AssignmentMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;

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
    private BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", length = 30)
    private AssignmentMode assignmentMode = AssignmentMode.AUTO_SUGGEST;

    @Column(name = "reassign_count")
    private Integer reassignCount = 0;

    @Column(name = "assignment_expires_at")
    private Instant assignmentExpiresAt;

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

    @Column(name = "end_confirmation_method", length = 30)
    private String endConfirmationMethod;

    /** Chuyến đã được chốt kết thúc: Tài xế kết thúc là đủ; dữ liệu cũ do Renter xác nhận/AUTO vẫn được chấp nhận. */
    public boolean isEndAccepted() {
        return driverEndConfirmedAt != null || renterEndConfirmedAt != null || renterConfirmedAt != null
                || "AUTO".equals(endConfirmationMethod);
    }

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

    /** Nhà xe đã chốt quyết toán nhưng chờ Renter chốt giờ kết thúc (cửa sổ 12 giờ) mới hoàn tất. */
    @Column(name = "settlement_requested_at")
    private Instant settlementRequestedAt;

    @Column(name = "driver_replacement_required", nullable = false, columnDefinition = "boolean default false")
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

    @Version
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

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingPricingItem> pricingItems = new ArrayList<>();

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

    // B1: 4 cột vehicleChange* lưu xe cũ/xe mới, giá hai xe và giảm trừ tại thời điểm đổi; không đổi totalAmount và cọc.
    /** Thời điểm đổi xe đã được khách chấp thuận (xe cũ nằm ở previousVehicleId). */
    @Column(name = "vehicle_change_at")
    private Instant vehicleChangeAt;

    /** Giá thuê xe cũ = giá trị hợp đồng ban đầu (totalAmount), không đổi sau khi đổi xe. */
    @Column(name = "vehicle_change_old_amount", precision = 14, scale = 2)
    private BigDecimal vehicleChangeOldAmount;

    /** Giá thuê xe mới cho cùng khoảng thời gian, chốt tại thời điểm đổi. */
    @Column(name = "vehicle_change_new_amount", precision = 14, scale = 2)
    private BigDecimal vehicleChangeNewAmount;

    /** Giảm trừ vào hóa đơn cuối = max(0, giá xe cũ - giá xe mới). Xe đắt hơn thì bằng 0. */
    @Column(name = "vehicle_change_discount", precision = 14, scale = 2)
    private BigDecimal vehicleChangeDiscount;

    /** Giảm trừ do đổi xe; ZERO nếu chưa có. Chỉ dùng khi tính nghĩa vụ cuối, không đổi totalAmount. */
    public BigDecimal vehicleChangeCredit() {
        return vehicleChangeDiscount == null ? BigDecimal.ZERO : vehicleChangeDiscount;
    }

    @jakarta.persistence.Embedded
    private OperationalProposal operationalProposal;

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
