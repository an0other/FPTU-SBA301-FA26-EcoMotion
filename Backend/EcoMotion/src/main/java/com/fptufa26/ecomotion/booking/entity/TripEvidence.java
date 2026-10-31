package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import com.fptufa26.ecomotion.user.entity.User;

@Entity
@Table(name = "trip_evidence")
@Getter
@Setter
@NoArgsConstructor
public class TripEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 50)
    private TripEvidenceType evidenceType;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "cloudinary_public_id", length = 255)
    private String cloudinaryPublicId;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (recordedAt == null) {
            recordedAt = now;
        }
    }
}
