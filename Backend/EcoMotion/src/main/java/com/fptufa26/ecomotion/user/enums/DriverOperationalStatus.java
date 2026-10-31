package com.fptufa26.ecomotion.user.enums;

/**
 * Trạng thái vận hành tổng hợp (tính toán động).
 * Kết hợp giữa trạng thái sẵn sàng, lịch trình tương lai và chuyến đi đang thực hiện.
 */
public enum DriverOperationalStatus {
    OFFLINE,       // Tài xế ngoại tuyến, không nhận chuyến mới
    AVAILABLE,     // Tài xế sẵn sàng nhận chuyến (không có chuyến hiện tại & không có lịch tương lai)
    UNAVAILABLE,   // Tài xế chủ động tạm nghỉ
    SCHEDULED,     // Tài xế có chuyến đã được phân công trong tương lai (rảnh ở hiện tại)
    ON_TRIP        // Tài xế đang thực hiện chuyến đi
}
