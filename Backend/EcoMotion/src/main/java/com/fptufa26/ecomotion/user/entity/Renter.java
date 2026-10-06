package com.fptufa26.ecomotion.user.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
import java.time.LocalDate;

@Entity
@Table(name = "renter")
@Getter
@Setter
@NoArgsConstructor
public class Renter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * CCCD (citizen ID) number, stored two-way encrypted (AES-256-GCM) at rest. Wider than the
     * plaintext because it holds Base64 ciphertext; kept deterministic so the partial UNIQUE index
     * {@code uk_renter_cccd_no} still works.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "cccd_no", length = 255)
    private String cccdNo;

    /** Identity fields auto-filled from the CCCD OCR during activation. */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(length = 255)
    private String address;

    @Column(name = "cccd_image_url", length = 500)
    private String cccdImageUrl;

    @Column(name = "cccd_image_back_url", length = 500)
    private String cccdImageBackUrl;

    /** Selfie uploaded for the CCCD face-match check. */
    @Column(name = "portrait_url", length = 500)
    private String portraitUrl;

    @Column(name = "license_no", length = 20)
    private String licenseNo;

    @Column(name = "license_class", length = 10)
    private String licenseClass;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Column(name = "license_verified", nullable = false)
    private boolean licenseVerified;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
