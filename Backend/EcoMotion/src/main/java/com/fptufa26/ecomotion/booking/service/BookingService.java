package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.BookingStatus;

import java.util.List;

public interface BookingService {

    CreateBookingResponse create(Long userId, CreateBookingRequest request);

    List<BookingSummaryResponse> listMine(Long userId, BookingStatus status);

    CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode);

    CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request);
}
