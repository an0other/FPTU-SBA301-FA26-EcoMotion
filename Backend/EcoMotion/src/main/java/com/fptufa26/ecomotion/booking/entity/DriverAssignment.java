package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.AssignmentStatus;
import com.fptufa26.ecomotion.booking.enums.DriverRejectReason;
import com.fptufa26.ecomotion.user.entity.Driver;
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

import java.time.Instant;

@Entity
@Table(name = "driver_assignment")
@Getter
@Setter
@NoArgsConstructor
public class DriverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status = AssignmentStatus.PENDING_RESPONSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 50)
    private DriverRejectReason rejectionReason;

    @Column(name = "rejection_note", length = 500)
    private String rejectionNote;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber = 1;

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
        if (assignedAt == null) {
            assignedAt = now;
        }
        if (status == null) {
            status = AssignmentStatus.PENDING_RESPONSE;
        }
        if (roundNumber == null) {
            roundNumber = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
