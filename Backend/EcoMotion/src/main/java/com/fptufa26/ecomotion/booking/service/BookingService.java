package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;

public interface BookingService {

    CreateBookingResponse create(Long userId, CreateBookingRequest request);

    CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode);

    CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request);
}
