package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;

public interface BookingService {

    CreateBookingResponse create(Long userId, CreateBookingRequest request);
}
