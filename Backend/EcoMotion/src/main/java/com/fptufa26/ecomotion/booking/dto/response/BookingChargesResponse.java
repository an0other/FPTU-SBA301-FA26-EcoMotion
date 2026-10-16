package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.util.List;

public record BookingChargesResponse(
        String bookingCode,
        BookingStatus bookingStatus,
        List<BookingChargeResponse> charges
) {
}