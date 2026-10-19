package com.fptufa26.ecomotion.vehicle.dto.request;

import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record UpdateRentalPlansRequest(
        @NotEmpty(message = "Danh sách gói cước không được để trống")
        List<@Valid PlanItemRequest> plans,

        @Min(value = 0, message = "Thời gian ân hạn quá giờ phải >= 0")
        Integer allowedOvertimeMinutes,

        @DecimalMin(value = "0.0", message = "Tỉ lệ phụ thu quá giờ phải >= 0")
        BigDecimal overtimeSurchargeRatio,

        @Min(value = 0, message = "Bán kính đón miễn phí phải >= 0")
        Integer freeRadiusKm,

        @DecimalMin(value = "0.0", message = "Đơn giá ngoài bán kính phải >= 0")
        BigDecimal outOfRadiusRate,

        @Min(value = 0, message = "Thời gian chờ miễn phí phải >= 0")
        Integer waitingFreeMinutes,

        @DecimalMin(value = "0.0", message = "Đơn giá mỗi phút chờ phải >= 0")
        BigDecimal waitingRate,

        @DecimalMin(value = "0.0", message = "Tỉ lệ phụ thu cuối tuần phải >= 0")
        BigDecimal weekendSurchargeRatio
) {

    public record PlanItemRequest(
            @NotNull(message = "Loại gói không được để trống")
            PlanType planType,

            @DecimalMin(value = "0.0", message = "Đơn giá phải >= 0")
            BigDecimal slotPrice,

            @NotNull(message = "Trạng thái gói không được để trống")
            PlanStatus status,

            @DecimalMin(value = "0.0", message = "Tiền trả trước phải >= 0")
            BigDecimal upfrontAmount,

            @Min(value = 1, message = "Ngưỡng chuyển ngày phải > 0")
            Integer hourlyToDailyThresholdMinutes
    ) {
    }
}

