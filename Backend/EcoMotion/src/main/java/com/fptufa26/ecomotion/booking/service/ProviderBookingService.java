package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.AddChargeRequest;
import com.fptufa26.ecomotion.booking.dto.response.AvailableDriverResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProviderBookingService {

    List<ProviderBookingResponse> listBookings(Long currentUserId, String status);

    Page<ProviderBookingResponse> listBookings(Long currentUserId, String status, Pageable pageable);

    PageResponse<ProviderBookingResponse> listBookingsPaged(Long currentUserId, String status, int offset, int limit);

    BookingSummaryResponse getSummary(Long currentUserId);

    ProviderBookingDetailResponse getBookingDetail(Long currentUserId, String bookingCodeOrId);

    ProviderBookingResponse acceptBooking(Long currentUserId, String bookingCodeOrId);

    ProviderBookingResponse denyBooking(Long currentUserId, String bookingCodeOrId, String reason);

    ProviderBookingResponse assignDriver(Long currentUserId, String bookingCodeOrId, Long driverId);

    List<BookingChargeResponse> getCharges(Long currentUserId, String bookingCodeOrId);

    BookingChargeResponse addCharge(Long currentUserId, String bookingCodeOrId, AddChargeRequest request);

    List<AvailableDriverResponse> getAvailableDrivers(Long currentUserId);

    byte[] exportBookingsCsv(Long currentUserId, String status);

    TripTrackingResponse getTripTracking(Long currentUserId, String bookingCodeOrId);

    TripLocationResponse getTripLocation(Long currentUserId, String bookingCodeOrId);
}
