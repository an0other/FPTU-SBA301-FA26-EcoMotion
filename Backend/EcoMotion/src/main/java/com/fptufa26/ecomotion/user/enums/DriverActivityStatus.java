package com.fptufa26.ecomotion.user.enums;

/**
 * Trạng thái chủ động của tài xế (lưu database).
 * Do Driver chủ động bật/tắt để điều khiển việc nhận cuốc.
 */
public enum DriverActivityStatus {
    OFFLINE,       // Tài xế ngoại tuyến, không nhận chuyến mới
    AVAILABLE,     // Tài xế sẵn sàng nhận chuyến
    UNAVAILABLE    // Tài xế chủ động tạm nghỉ
}
