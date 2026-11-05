package com.fptufa26.ecomotion.booking.dto.response;

public record OperationalRiskResponse(
        boolean atRisk,
        String riskType,
        String severity,
        String userMessage,
        String recommendedAction
) {
    public static OperationalRiskResponse safe() {
        return new OperationalRiskResponse(false, null, null, null, null);
    }

    public static OperationalRiskResponse vehicleBusy() {
        return new OperationalRiskResponse(true, "VEHICLE_BUSY", "WARNING",
                "Phương tiện đang thực hiện một chuyến đi khác và có thể ảnh hưởng đến thời gian nhận xe của bạn. Nhà cung cấp đang kiểm tra khả năng đáp ứng lịch trình.",
                "Chờ nhà cung cấp cập nhật phương án.");
    }
}
