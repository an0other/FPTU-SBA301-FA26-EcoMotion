package com.fptufa26.ecomotion.booking.dto.response;

public record BookingContractResponse(
        String bookingCode,
        boolean contractUploaded
) {
}