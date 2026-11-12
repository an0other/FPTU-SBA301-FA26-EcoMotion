package com.fptufa26.ecomotion.verification.identity.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

/** Immutable application audit row. No update/delete API is exposed. */
@Entity
@Table(name = "identity_review_event")
@Immutable
@Getter
public class IdentityReviewEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false) private IdentityVerificationAttempt attempt;
    @Column(nullable = false, length = 30) private String eventType;
    @Column(nullable = false, length = 20) private String actorType;
    @Column(nullable = false) private Long actorId;
    @Column(length = 100) private String reasonCode;
    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "text") private String note;
    @Column(length = 100) private String policyVersion;
    @Column(nullable = false) private int documentRevision;
    @Column(nullable = false) private long attemptVersion;
    @Column(nullable = false) private Instant occurredAt;
    @Convert(converter = EncryptedStringConverter.class) @Column(columnDefinition = "text") private String documentJson;
    @Column(columnDefinition = "text") private String videoJson;
    @Column(length = 100) private String frontKey;
    @Column(length = 100) private String backKey;
    @Column(length = 100) private String videoKey;
    @Column(length = 64) private String frontSha256;
    @Column(length = 64) private String backSha256;
    @Column(length = 64) private String videoSha256;

    protected IdentityReviewEvent() {}

    public IdentityReviewEvent(IdentityVerificationAttempt attempt, String eventType, String actorType,
            Long actorId, String reasonCode, String note, Instant now) {
        this.attempt = attempt;
        this.eventType = eventType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reasonCode = reasonCode;
        this.note = note;
        this.occurredAt = now;
        this.policyVersion = attempt.getDecisionPolicyVersion();
        this.documentRevision = attempt.getDocumentRevision();
        this.attemptVersion = attempt.getVersion();
        this.documentJson = attempt.getDocumentJson();
        this.videoJson = attempt.getVideoJson();
        this.frontKey = attempt.getFrontKey();
        this.backKey = attempt.getBackKey();
        this.videoKey = attempt.getVideoKey();
        this.frontSha256 = attempt.getFrontSha256();
        this.backSha256 = attempt.getBackSha256();
        this.videoSha256 = attempt.getVideoSha256();
    }
}
