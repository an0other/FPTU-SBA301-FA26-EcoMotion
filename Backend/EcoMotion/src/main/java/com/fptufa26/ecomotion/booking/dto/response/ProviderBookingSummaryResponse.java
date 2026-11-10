package com.fptufa26.ecomotion.booking.dto.response;

public record ProviderBookingSummaryResponse(
        long total,
        long pending,
        long awaitingDeposit,
        long accepted,
        long confirmed,
        long inProgress,
        long completed,
        long rejected,
        long cancelled,
        java.util.List<String> vehicleOptions,
        /** Số đơn khách đã báo chuyển khoản, chờ nhà xe xác nhận. */
        long paymentAwaitingConfirmation
) {
}
