package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.BookingReviewContext;

public interface BookingReviewAccessService {

    BookingReviewContext getOwnedBooking(
            Long userId,
            String bookingCode);
}