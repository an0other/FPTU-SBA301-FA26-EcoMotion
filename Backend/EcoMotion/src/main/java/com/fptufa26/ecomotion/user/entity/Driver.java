package com.fptufa26.ecomotion.user.entity;

import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
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
import java.time.LocalDate;

@Entity
@Table(name = "driver")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "license_no", nullable = false, length = 20)
    private String licenseNo;

    @Column(name = "license_class", nullable = false, length = 10)
    private String licenseClass;

    @Column(name = "license_expiry", nullable = false)
    private LocalDate licenseExpiry;

    @Column(name = "portrait_url", nullable = false, length = 500)
    private String portraitUrl;

    @Column(name = "license_image_url", nullable = false, length = 500)
    private String licenseImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverStatus status = DriverStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_status", nullable = false, length = 20)
    private DriverActivityStatus activityStatus = DriverActivityStatus.AVAILABLE;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

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
        if (statusUpdatedAt == null) {
            statusUpdatedAt = now;
        }
        if (status == null) {
            status = DriverStatus.PENDING;
        }
        if (activityStatus == null) {
            activityStatus = DriverActivityStatus.AVAILABLE;
        }
        if (ratingAvg == null) {
            ratingAvg = BigDecimal.ZERO;
        }
        if (ratingCount == null) {
            ratingCount = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
