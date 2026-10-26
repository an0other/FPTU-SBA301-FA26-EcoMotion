package com.fptufa26.ecomotion.booking.dto.response;

public record OperationalRiskResponse(
        boolean atRisk,
        String blockingBookingCode,
        String message
) {
    public static OperationalRiskResponse safe() {
        return new OperationalRiskResponse(false, null, null);
    }
}
