package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class OperationalProposal {
    @Column(name = "proposal_id", length = 36)
    private String id;
    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_resolution", length = 30)
    private OperationalResolutionType resolution;
    @Column(name = "proposal_vehicle_id")
    private Long vehicleId;
    @Column(name = "proposal_pickup_time")
    private Instant pickupTime;
    @Column(name = "proposal_reason", length = 500)
    private String reason;
    @Column(name = "proposal_created_by")
    private Long createdBy;
    @Column(name = "proposal_created_at")
    private Instant createdAt;
    @Column(name = "proposal_accepted")
    private Boolean accepted;
    @Column(name = "proposal_responded_by")
    private Long respondedBy;
    @Column(name = "proposal_responded_at")
    private Instant respondedAt;
    @Column(name = "proposal_response_reason", length = 500)
    private String responseReason;
}
