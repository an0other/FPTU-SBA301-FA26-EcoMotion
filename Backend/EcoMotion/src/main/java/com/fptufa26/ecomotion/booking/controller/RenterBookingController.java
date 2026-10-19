package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.request.CheckoutRequest;
import com.fptufa26.ecomotion.booking.dto.request.CreateBookingRequest;
import com.fptufa26.ecomotion.booking.dto.request.OpenDisputeRequest;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.CancelBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmEndResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.vehicle.dto.response.BookableVehicleResponse;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/renter/bookings")
@RequiredArgsConstructor
@Tag(name = "renter-bookings")
public class RenterBookingController {

    private final BookingService bookingService;
    private final VehicleCatalogService vehicleCatalogService;

    @GetMapping
    public ApiResponse<List<BookingSummaryResponse>> listMine(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(required = false) BookingStatus status) {
        return ApiResponse.success(bookingService.listMine(user.getUserId(), status));
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<BookableVehicleResponse>> listVehicles() {
        return ApiResponse.success(vehicleCatalogService.listApproved());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateBookingResponse>> create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse created = bookingService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{bookingCode}/checkout")
    public ApiResponse<CheckoutPreviewResponse> previewCheckout(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.previewCheckout(user.getUserId(), bookingCode));
    }

    @PostMapping("/{bookingCode}/checkout")
    public ApiResponse<CheckoutResultResponse> checkout(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.success(bookingService.checkout(user.getUserId(), bookingCode, request));
    }

    @GetMapping("/{bookingCode}")
    public ApiResponse<BookingDetailResponse> detail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.detail(user.getUserId(), bookingCode));
    }

    @PostMapping("/{bookingCode}/cancel")
    public ApiResponse<CancelBookingResponse> cancel(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.cancel(user.getUserId(), bookingCode));
    }

    @PostMapping("/{bookingCode}/confirm-end")
    public ApiResponse<ConfirmEndResponse> confirmEnd(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.confirmEnd(user.getUserId(), bookingCode));
    }

    @PostMapping("/{bookingCode}/disputes")
    public ApiResponse<DisputeOpenedResponse> openDispute(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @Valid @RequestBody OpenDisputeRequest request) {
        return ApiResponse.success(bookingService.openDispute(user.getUserId(), bookingCode, request));
    }
}
