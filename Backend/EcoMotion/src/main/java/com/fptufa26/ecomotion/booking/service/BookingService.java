package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.PickupQuoteRequest;
import com.fptufa26.ecomotion.booking.dto.response.PickupQuoteResponse;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.booking.dto.request.UpdateBookingRequestDetails;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingRequestDetailsResponse;
import com.fptufa26.ecomotion.booking.dto.response.CancelBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmEndResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmStartResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.fptufa26.ecomotion.booking.dto.request.RentalQuoteRequest;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BookingService {

    RentalQuoteResponse quoteRental(
            Long userId, RentalQuoteRequest request);

    PickupQuoteResponse quotePickup(PickupQuoteRequest request);

    CreateBookingResponse create(Long userId, CreateBookingRequest request);

    List<BookingSummaryResponse> listMine(Long userId, BookingStatus status);

    PageResponse<BookingSummaryResponse> listMinePage(
            Long userId, BookingStatus status, String group, String keyword, String sort, int page, int size);

    CheckoutPreviewResponse previewCheckout(Long userId, String bookingCode);

    CheckoutResultResponse checkout(Long userId, String bookingCode, CheckoutRequest request);

    BookingDetailResponse detail(Long userId, String bookingCode);

    TripTrackingResponse getTripTracking(Long userId, String bookingCode);

    BookingRequestDetailsResponse updateRequestDetails(
            Long userId, String bookingCode, UpdateBookingRequestDetails request);

    CancelBookingResponse cancel(Long userId, String bookingCode);

    ConfirmStartResponse confirmStart(Long userId, String bookingCode);

    ConfirmEndResponse confirmEnd(Long userId, String bookingCode);

    DisputeOpenedResponse openDispute(Long userId, String bookingCode, OpenDisputeRequest request);

    String uploadDisputeEvidence(Long userId, String bookingCode, MultipartFile file);

    // Admin methods
    List<Booking> listAdminBookings(BookingStatus status, String keyword);

    Optional<Booking> getBookingById(Long bookingId);

    List<BookingCharge> getChargesForBooking(Long bookingId);

    List<PaymentTransaction> listTransactions(PaymentStatus status, PaymentType type);

    List<PaymentTransaction> getTransactionsForBooking(Long bookingId);

    PaymentTransaction processMockRefund(Long bookingId, BigDecimal amount, String reason);

    long countBookingsByStatus(BookingStatus status);

    long countTotalBookings();

    Optional<Booking> findByBookingCode(String bookingCode);

    Booking saveBooking(Booking booking);

    BigDecimal sumTotalRevenue();

    BigDecimal sumAdminCommission();

    BigDecimal sumCompletedGrossRentalVolume();

    BigDecimal sumEscrowDeposits();

    BigDecimal sumTotalRevenue(Instant from, Instant to);
    BigDecimal sumAdminCommission(Instant from, Instant to);
    BigDecimal sumCompletedGrossRentalVolume(Instant from, Instant to);
    BigDecimal sumEscrowDeposits(Instant from, Instant to);
    long countCompletedBookings(Instant from, Instant to);
    long countTotalBookings(Instant from, Instant to);
}
