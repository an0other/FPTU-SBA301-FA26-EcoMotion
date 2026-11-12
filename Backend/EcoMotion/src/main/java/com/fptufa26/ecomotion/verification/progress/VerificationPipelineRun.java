package com.fptufa26.ecomotion.verification.progress;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name="verification_pipeline_run",indexes={@Index(name="ix_verification_pipeline_owner_attempt",columnList="owner_user_id,attempt_id,started_at"),@Index(name="ix_verification_pipeline_owner_kind",columnList="owner_user_id,kind,started_at")})
public class VerificationPipelineRun {
    @Id private UUID id;
    @Column(name="owner_user_id",nullable=false) private Long ownerUserId;
    @Column(name="attempt_id") private UUID attemptId;
    @Column(nullable=false,length=20) private String kind;
    @Column(nullable=false) private int revision;
    @Column(nullable=false,length=20) private String status;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="finished_at") private Instant finishedAt;
    @Column(name="progress_json",columnDefinition="text") private String progressJson;
    @Column(name="error_code",length=100) private String errorCode;
    @Version private Long version;
}
