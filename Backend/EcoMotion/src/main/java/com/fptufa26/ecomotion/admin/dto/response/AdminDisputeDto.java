package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDisputeDto {
    private Long id;
    private Long bookingId;
    private String bookingCode;
    private Long chargeId;
    private String chargeDescription;
    private BigDecimal chargeAmount;
    private Long raisedById;
    private String raisedByName;
    private String raisedByEmail;
    private Long handledById;
    private String handledByName;
    private DisputeCategory category;
    private DisputeStatus status;
    private String description;
    private List<String> evidenceUrls;
    private String resolution;
    private Instant createdAt;
    private Instant updatedAt;
}
