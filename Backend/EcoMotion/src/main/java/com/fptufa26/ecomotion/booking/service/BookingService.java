package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.booking.dto.request.UpdateBookingRequestDetails;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingRequestDetailsResponse;
import com.fptufa26.ecomotion.booking.dto.response.CancelBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmEndResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BookingService {

    CreateBookingResponse create(Long userId, CreateBookingRequest request);

    List<BookingSummaryResponse> listMine(Long userId, BookingStatus status);

    CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode);

    CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request);

    BookingDetailResponse detail(Long userId, String bookingCode);

    BookingRequestDetailsResponse updateRequestDetails(
            Long userId, String bookingCode, UpdateBookingRequestDetails request);

    CancelBookingResponse cancel(Long userId, String bookingCode);

    ConfirmEndResponse confirmEnd(Long userId, String bookingCode);

    DisputeOpenedResponse openDispute(Long userId, String bookingCode, OpenDisputeRequest request);

    // Admin methods
    List<Booking> listAdminBookings(BookingStatus status, String keyword);

    Optional<Booking> getBookingById(Long bookingId);

    List<BookingCharge> getChargesForBooking(Long bookingId);

    List<PaymentTransaction> listTransactions(PaymentStatus status, PaymentType type);

    List<PaymentTransaction> getTransactionsForBooking(Long bookingId);

    PaymentTransaction processMockRefund(Long bookingId, BigDecimal amount, String reason);

    long countBookingsByStatus(BookingStatus status);

    long countTotalBookings();

    BigDecimal sumTotalRevenue();
}
