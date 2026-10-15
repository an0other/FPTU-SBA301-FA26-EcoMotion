package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.util.List;

public interface ProviderBookingService {
    List<ProviderBookingResponse> getProviderBookings(Long userId, BookingStatus status);
}
