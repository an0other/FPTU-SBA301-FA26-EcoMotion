package com.fptufa26.ecomotion.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "provider_application_review")
@Getter
@Setter
public class ProviderApplicationReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "application_version", nullable = false)
    private Long applicationVersion;
    @Column(name = "admin_id", nullable = false)
    private Long adminId;
    @Column(nullable = false, length = 30)
    private String decision;
    @Column(name = "reason_code", length = 100)
    private String reasonCode;
    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Convert(converter = com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter.class)
    @Column(name = "profile_snapshot_json", columnDefinition = "text")
    private String profileSnapshotJson;

    @Column(name = "submitted_attempt_id")
    private java.util.UUID submittedAttemptId;
}
