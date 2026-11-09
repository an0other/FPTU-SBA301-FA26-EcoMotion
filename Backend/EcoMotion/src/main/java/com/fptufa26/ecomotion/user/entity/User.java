package com.fptufa26.ecomotion.user.entity;

import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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
import org.hibernate.annotations.Check;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_users_phone_role", columnNames = {"phone", "role"}),
                @UniqueConstraint(name = "uk_users_identity_role", columnNames = {"identity_id", "role"})
        }
)
@Check(name = "ck_users_identity_role", constraints = "identity_id is null or role in ('RENTER', 'PROVIDER')")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 255)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id", foreignKey = @ForeignKey(name = "fk_users_identity"))
    private PersonIdentity identity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ekyc_status", nullable = false, length = 20)
    private EkycStatus ekycStatus = EkycStatus.UNVERIFIED;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "identity_reviewed_at")
    private Instant identityReviewedAt;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        normalize();
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = UserStatus.PENDING;
        }
        if (ekycStatus == null) {
            ekycStatus = EkycStatus.UNVERIFIED;
        }
    }

    @PreUpdate
    void onUpdate() {
        normalize();
        updatedAt = Instant.now();
    }

    public void normalize() {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
        if (phone != null) {
            phone = phone.trim();
        }
    }

    public Long getUserId() {
        return id;
    }

    public void setUserId(Long userId) {
        this.id = userId;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
