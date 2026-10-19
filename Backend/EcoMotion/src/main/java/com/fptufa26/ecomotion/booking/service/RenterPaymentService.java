package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargesResponse;
import com.fptufa26.ecomotion.booking.dto.response.PaymentHistoryResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;

public interface RenterPaymentService {

    PageResponse<PaymentHistoryResponse> getPaymentHistory(
            Long userId,
            int page,
            int size);

    BookingChargesResponse getBookingCharges(
            Long userId,
            String bookingCode);
}