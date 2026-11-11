package com.fptufa26.ecomotion.verification.identity.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import com.fptufa26.ecomotion.verification.identity.enums.IdentityVerificationMethod;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "person_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_person_identity_cccd_fingerprint", columnNames = "cccd_fingerprint")
})
@Check(name = "ck_person_identity_fingerprint", constraints = "length(cccd_fingerprint) = 64 and fingerprint_key_version > 0")
@Check(name = "ck_person_identity_review", constraints = "verification_method <> 'ADMIN_REVIEW' or (reviewed_by is not null and reviewed_at is not null)")
@Getter
@Setter
@NoArgsConstructor
public class PersonIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cccd_ciphertext", nullable = false, length = 255)
    private String cccdNo;

    @Column(name = "cccd_fingerprint", nullable = false, length = 64)
    private String cccdFingerprint;

    @Column(name = "fingerprint_key_version", nullable = false)
    private Integer fingerprintKeyVersion;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(length = 500)
    private String address;

    @Column(name = "document_expiry")
    private LocalDate documentExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonIdentityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 30)
    private IdentityVerificationMethod verificationMethod;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    @Column(name = "approved_attempt_id")
    private Long approvedAttemptId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
