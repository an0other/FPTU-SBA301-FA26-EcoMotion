package com.fptufa26.ecomotion.verification.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_account_link")
@Getter
@Setter
public class IdentityAccountLink {
    @Id private UUID id;
    @Column(name = "source_user_id", nullable = false) private Long sourceUserId;
    @Column(name = "target_user_id", nullable = false) private Long targetUserId;
    @Column(name = "identity_id", nullable = false) private Long identityId;
    @Column(name = "source_credential_stamp", nullable = false, length = 64) private String sourceCredentialStamp;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Version private Long version;
}
