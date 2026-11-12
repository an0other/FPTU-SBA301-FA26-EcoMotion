package com.fptufa26.ecomotion.verification.identity.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityAttemptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_verification_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_identity_attempt_public", columnNames = "public_id"),
        @UniqueConstraint(name = "uk_identity_attempt_active_user", columnNames = "active_user_id")
})
@Check(name = "ck_identity_attempt_owner", constraints = "active_user_id is null or active_user_id = user_id")
@Check(name = "ck_identity_attempt_role", constraints = "role in ('RENTER', 'PROVIDER')")
@Getter
@Setter
@NoArgsConstructor
public class IdentityVerificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_identity_attempt_user"))
    private User user;

    @Column(name = "active_user_id")
    private Long activeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdentityAttemptStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_actor", length = 20)
    private String decisionActor;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "decision_reason", length = 100)
    private String decisionReason;

    @Column(name = "decision_policy_version", length = 100)
    private String decisionPolicyVersion;

    @Column(name = "resulting_identity_id")
    private Long resultingIdentityId;

    @Column(name = "operation_id")
    private UUID operationId;

    @Column(name = "processing_stage", length = 20)
    private String processingStage;

    @Column(name = "operation_started_at")
    private Instant operationStartedAt;

    @Column(name = "document_revision", nullable = false)
    private int documentRevision;

    @Column(name = "front_key", length = 100)
    private String frontKey;

    @Column(name = "back_key", length = 100)
    private String backKey;

    @Column(name = "video_key", length = 100)
    private String videoKey;

    @Column(name = "front_sha256", length = 64)
    private String frontSha256;

    @Column(name = "back_sha256", length = 64)
    private String backSha256;
    @Column(length = 64)
    private String videoSha256;
    @Column(length = 64)
    private String requiredPreviousFrontSha;
    @Column(length = 64)
    private String requiredPreviousBackSha;
    @Column(length = 100)
    private String requiredPreviousVideoSha;
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean evidenceRetained;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_json", columnDefinition = "text")
    private String documentJson;

    @Column(name = "video_json", columnDefinition = "text")
    private String videoJson;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "attempt", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("id ASC")
    private java.util.List<IdentityReviewEvent> reviewHistory = new java.util.ArrayList<>();

    public boolean hasRequiredNewEvidence() {
        return (requiredPreviousFrontSha == null || !java.util.Objects.equals(requiredPreviousFrontSha, frontSha256)
                || !java.util.Objects.equals(requiredPreviousBackSha, backSha256))
                && (requiredPreviousVideoSha == null || !java.util.Objects.equals(requiredPreviousVideoSha,
                        videoSha256 == null ? videoKey : videoSha256));
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
