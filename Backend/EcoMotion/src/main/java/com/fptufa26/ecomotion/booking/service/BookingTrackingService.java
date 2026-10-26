package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;

public interface BookingTrackingService {

    TripTrackingResponse buildTrackingResponse(Booking booking);

    TripLocationResponse getLatestLocation(Long bookingId);
}
