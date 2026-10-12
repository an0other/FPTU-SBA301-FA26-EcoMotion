package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;

import java.util.List;

public interface ProviderBookingService {

    /**
     * Bookings of vehicles owned by the provider behind {@code currentUserId}.
     *
     * @param status optional {@code BookingStatus} name; blank/null means no filter
     */
    List<ProviderBookingResponse> listBookings(Long currentUserId, String status);
}
