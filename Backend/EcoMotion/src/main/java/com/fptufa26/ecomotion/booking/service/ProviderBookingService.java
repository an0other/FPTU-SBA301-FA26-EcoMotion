package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.AddBookingChargeRequest;
import com.fptufa26.ecomotion.booking.dto.request.AssignDriverRequest;
import com.fptufa26.ecomotion.booking.dto.request.RejectBookingRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;

import java.util.List;

public interface ProviderBookingService {

    List<ProviderBookingResponse> getProviderBookings(Long userId, BookingStatus status);

    ProviderBookingDetailResponse getBookingDetail(Long userId, String bookingIdentifier);

    ProviderBookingDetailResponse acceptBooking(Long userId, String bookingIdentifier);

    ProviderBookingDetailResponse rejectBooking(Long userId, String bookingIdentifier, RejectBookingRequest request);

    List<DriverAvailabilityResponse> getAvailableDrivers(Long userId, String bookingIdentifier);

    ProviderBookingDetailResponse assignDriver(Long userId, String bookingIdentifier, AssignDriverRequest request);

    BookingChargeResponse addManualCharge(Long userId, String bookingIdentifier, AddBookingChargeRequest request);

    void removeManualCharge(Long userId, String bookingIdentifier, Long chargeId);
}
