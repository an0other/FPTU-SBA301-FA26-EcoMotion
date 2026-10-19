package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;

public record BookingContractStatusResponse(
        String bookingCode,
        BookingStatus bookingStatus,
        String vehicleName,
        PlanType planType,
        boolean contractUploaded,
        boolean canUpload,
        boolean canDownload,
        String restrictionReason
) {
}