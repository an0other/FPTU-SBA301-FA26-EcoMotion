package com.fptufa26.ecomotion.admin.dto.request;

import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.enums.DisputeResolutionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

@Data
public class DisputeResolveRequest {
    @NotNull(message = "Trạng thái không được để trống")
    private DisputeStatus status;

    private DisputeResolutionDecision decision;

    @NotBlank(message = "Nội dung giải quyết không được để trống")
    @Size(max = 1000, message = "Kết luận tối đa 1000 ký tự")
    private String resolution;

    @Digits(integer = 12, fraction = 2)
    private BigDecimal refundAwardTotal;

    private String tripEndAction;

    /** Khiếu nại giờ kết thúc, phán quyết ACCEPT_RENTER_TIME: giờ kết thúc đúng do admin xác minh. */
    private java.time.Instant correctedEndAt;
}
