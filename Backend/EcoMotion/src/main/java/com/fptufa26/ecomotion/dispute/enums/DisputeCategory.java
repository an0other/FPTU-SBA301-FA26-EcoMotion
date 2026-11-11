package com.fptufa26.ecomotion.dispute.enums;

public enum DisputeCategory {
    CHARGE,
    SERVICE_QUALITY,
    DEPOSIT_REFUND,
    TRIP_NOT_CONFIRMED,
    /** Số tiền nhà xe ghi nhận khác số khách thực chuyển. */
    PAYMENT_MISMATCH,
    /** Tình huống chưa có loại riêng — khách bắt buộc mô tả. */
    OTHER,
    YEARLY_SETTLEMENT,
    YEARLY_CANCELLATION,
    YEARLY_REFUND,
    YEARLY_INCIDENT
}
