package com.fptufa26.ecomotion.booking.dto.response;

import java.time.Instant;

/**
 * Rủi ro vận hành của đơn: xe đang chạy chuyến trước và có thể về trễ so với giờ nhận xe.
 * Các trường blocking* / estimated* cho biết chuyến nào đang giữ xe, lịch trả xe, dự kiến trễ bao lâu.
 */
public record OperationalRiskResponse(
        boolean atRisk,
        String riskType,
        String severity,
        String userMessage,
        String recommendedAction,
        String blockingBookingCode,
        Instant blockingPlannedEndAt,
        Instant estimatedReadyAt,
        Long estimatedDelayMinutes
) {
    public static OperationalRiskResponse safe() {
        return new OperationalRiskResponse(false, null, null, null, null, null, null, null, null);
    }

    /** Giữ tương thích: cảnh báo chung khi không có dữ liệu lịch của chuyến trước. */
    public static OperationalRiskResponse vehicleBusy() {
        return new OperationalRiskResponse(true, "VEHICLE_BUSY", "WARNING",
                "Phương tiện đang thực hiện một chuyến đi khác và có thể ảnh hưởng đến thời gian nhận xe của bạn. Nhà cung cấp đang kiểm tra khả năng đáp ứng lịch trình.",
                "Chờ nhà cung cấp cập nhật phương án.", null, null, null, null);
    }

    public static OperationalRiskResponse vehicleLate(String blockingBookingCode, Instant plannedEndAt,
                                                      Instant estimatedReadyAt, long delayMinutes, boolean overdue) {
        String message = overdue
                ? "Chuyến trước của xe đã quá giờ trả theo lịch và chưa kết thúc. Xe dự kiến sẵn sàng trễ khoảng "
                    + delayMinutes + " phút so với giờ nhận của bạn. Nhà cung cấp đang kiểm tra phương án."
                : "Lịch trả xe của chuyến trước sát giờ nhận của bạn. Xe dự kiến sẵn sàng trễ khoảng "
                    + delayMinutes + " phút. Nhà cung cấp đang kiểm tra phương án.";
        return new OperationalRiskResponse(true, "VEHICLE_BUSY", delayMinutes >= 60 ? "CRITICAL" : "WARNING",
                message, "Chờ nhà cung cấp cập nhật phương án.", blockingBookingCode, plannedEndAt,
                estimatedReadyAt, delayMinutes);
    }
}
