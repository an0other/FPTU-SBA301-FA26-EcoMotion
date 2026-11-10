package com.fptufa26.ecomotion.user.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import com.fptufa26.ecomotion.user.enums.ProviderApplicationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "provider_application")
@Getter
@Setter
public class ProviderApplication {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderApplicationStatus status = ProviderApplicationStatus.DRAFT;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "profile_json", columnDefinition = "text")
    private String profileJson;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;
    @Column(length = 1000)
    private String reviewNote;

    @Column(name = "submitted_identity_id")
    private Long submittedIdentityId;

    @Column(name = "submitted_attempt_id")
    private java.util.UUID submittedAttemptId;

    @Version
    @Column(nullable = false)
    private Long version;
}
