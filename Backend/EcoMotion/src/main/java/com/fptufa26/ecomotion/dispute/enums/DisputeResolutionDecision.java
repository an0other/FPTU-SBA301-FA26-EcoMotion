package com.fptufa26.ecomotion.dispute.enums;

public enum DisputeResolutionDecision {
    WAIVE_CHARGE,
    KEEP_CHARGE,
    /** Giảm một phần phụ phí — số tiền giảm nằm ở refundAwardTotal. */
    PARTIAL_WAIVE
}
