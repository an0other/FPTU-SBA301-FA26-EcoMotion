package com.fptufa26.ecomotion.verification.license;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import com.fptufa26.ecomotion.user.entity.Renter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "driving_license_submission") @Getter @Setter
public class DrivingLicenseSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "renter_id", nullable = false) private Renter renter;
    @Version @Column(nullable = false) private Long version = 0L;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private boolean noQrDeclared;
    @Convert(converter = EncryptedStringConverter.class) @Column(nullable = false, columnDefinition = "text") private String documentJson;
    @Convert(converter = EncryptedStringConverter.class) @Column(nullable = false, columnDefinition = "text") private String evidenceJson;
    @Convert(converter = EncryptedStringConverter.class) @Column(nullable = false, columnDefinition = "text") private String historyJson;
    @Column(nullable = false, length = 500) private String frontPath;
    @Column(nullable = false, length = 500) private String backPath;
    @Column(nullable = false) private Instant createdAt;
    private Instant submittedAt;
}
