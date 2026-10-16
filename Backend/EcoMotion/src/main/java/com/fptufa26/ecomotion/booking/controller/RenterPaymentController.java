package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargesResponse;
import com.fptufa26.ecomotion.booking.dto.response.PaymentHistoryResponse;
import com.fptufa26.ecomotion.booking.service.RenterPaymentService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/renter")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RENTER')")
@Tag(name = "Renter Payments")
public class RenterPaymentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RenterPaymentService renterPaymentService;

    @GetMapping("/payments")
    @Operation(summary = "Get the authenticated renter's payment history")
    public ApiResponse<PageResponse<PaymentHistoryResponse>> getPaymentHistory(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Size must not exceed " + MAX_PAGE_SIZE);
        }

        return ApiResponse.success(
                renterPaymentService.getPaymentHistory(
                        principal.getUserId(),
                        page,
                        size));
    }

    @GetMapping("/bookings/{bookingCode}/charges")
    @Operation(summary = "Get charges for a booking owned by the authenticated renter")
    public ApiResponse<BookingChargesResponse> getBookingCharges(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable String bookingCode) {
        return ApiResponse.success(
                renterPaymentService.getBookingCharges(
                        principal.getUserId(),
                        bookingCode));
    }
}