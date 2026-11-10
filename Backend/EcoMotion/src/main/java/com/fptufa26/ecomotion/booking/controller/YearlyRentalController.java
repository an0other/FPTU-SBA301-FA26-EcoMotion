package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.service.YearlyRentalQuoteService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/renter/yearly-bookings/{bookingCode}", "/api/provider/yearly-bookings/{bookingCode}"})
@PreAuthorize("hasAnyRole('RENTER','PROVIDER')")
public class YearlyRentalController {
    private final YearlyRentalQuoteService service;
    private final PaymentRequestService payments;

    @GetMapping("/payments")
    public ApiResponse<java.util.List<PaymentRequestService.YearlyPaymentItem>> payments(
            @AuthenticationPrincipal SecurityUser principal, @PathVariable String bookingCode) {
        return ApiResponse.success(payments.yearlyPayments(principal.getUserId(), bookingCode));
    }

    @GetMapping("/schedule-preview")
    @PreAuthorize("hasRole('RENTER')")
    public ApiResponse<YearlyRentalQuoteService.Quote> preview(
            @AuthenticationPrincipal SecurityUser principal, @PathVariable String bookingCode) {
        return ApiResponse.success(service.quote(principal.getUserId(), bookingCode));
    }
}
