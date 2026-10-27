package com.fptufa26.ecomotion.booking.controller;

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
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.response.PageResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping({"/api/v1/renter/bookings", "/api/renter/bookings"})
@RequiredArgsConstructor
@Tag(name = "renter-bookings")
public class RenterBookingController {

    private final BookingService bookingService;
    private final VehicleCatalogService vehicleCatalogService;
    private final VehicleOperationalRiskService vehicleOperationalRiskService;
    private final VnpayService vnpayService;

    @GetMapping
    public ApiResponse<PageResponse<BookingSummaryResponse>> listMine(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(bookingService.listMinePage(
                user.getUserId(), status, group, keyword, sort, page, size));
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<BookableVehicleResponse>> listVehicles() {
        return ApiResponse.success(vehicleCatalogService.listApproved());
    }

    @PostMapping("/pickup-quote")
    public ApiResponse<PickupQuoteResponse> quotePickup(@Valid @RequestBody PickupQuoteRequest request) {
        return ApiResponse.success(bookingService.quotePickup(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateBookingResponse>> create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse created = bookingService.create(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/{bookingCode}/checkout")
    public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> previewCheckout(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.previewCheckout(user.getUserId(), bookingCode)));
    }

    @PostMapping("/{bookingCode}/checkout")
    public ResponseEntity<ApiResponse<CheckoutResultResponse>> checkout(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.checkout(user.getUserId(), bookingCode, request)));
    }

    @GetMapping("/{bookingCode}")
    public ApiResponse<BookingDetailResponse> detail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.detail(user.getUserId(), bookingCode));
    }

    @GetMapping("/{bookingCode}/operational-risk")
    public ApiResponse<com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse> operationalRisk(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(vehicleOperationalRiskService.assessForRenter(user.getUserId(), bookingCode));
    }

    @GetMapping("/{bookingCode}/tracking")
    public ApiResponse<TripTrackingResponse> tracking(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.getTripTracking(user.getUserId(), bookingCode));
    }

    @PatchMapping("/{bookingCode}/request-details")
    public ApiResponse<BookingRequestDetailsResponse> updateRequestDetails(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @Valid @RequestBody UpdateBookingRequestDetails request) {
        return ApiResponse.success(bookingService.updateRequestDetails(user.getUserId(), bookingCode, request));
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

    @PostMapping("/{bookingCode}/confirm-start")
    public ApiResponse<ConfirmStartResponse> confirmStart(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(bookingService.confirmStart(user.getUserId(), bookingCode));
    }

    @PostMapping("/{bookingCode}/disputes")
    public ApiResponse<DisputeOpenedResponse> openDispute(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @Valid @RequestBody OpenDisputeRequest request) {
        return ApiResponse.success(bookingService.openDispute(user.getUserId(), bookingCode, request));
    }

    @PostMapping(value = "/{bookingCode}/disputes/upload-evidence", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<java.util.Map<String, String>> uploadDisputeEvidence(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String url = bookingService.uploadDisputeEvidence(user.getUserId(), bookingCode, file);
        return ApiResponse.success(java.util.Map.of("url", url));
    }

    @GetMapping({"/{bookingCode}/settlement", "/{bookingCode}/settlements"})
    public ApiResponse<RenterSettlementResponse> getSettlement(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode) {
        return ApiResponse.success(vnpayService.getSettlementDetails(user.getUserId(), bookingCode));
    }

    @PostMapping({"/{bookingCode}/payments/final/create", "/{bookingCode}/payments/settlement/create"})
    public ApiResponse<VnpayPaymentUrlResponse> createFinalPayment(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String bookingCode,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        return ApiResponse.success(vnpayService.createFinalPaymentUrl(user.getUserId(), bookingCode, clientIp));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

