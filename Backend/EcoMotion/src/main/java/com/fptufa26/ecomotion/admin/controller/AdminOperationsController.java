package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.admin.dto.request.DisputeResolveRequest;
import com.fptufa26.ecomotion.admin.dto.request.RefundRequest;
import com.fptufa26.ecomotion.admin.dto.response.AdminBookingDto;
import com.fptufa26.ecomotion.admin.dto.response.AdminDisputeDto;
import com.fptufa26.ecomotion.admin.dto.response.AdminPaymentDto;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
@Tag(name = "Admin - Operations", description = "A04: Quản lý vận hành (Tab Đơn thuê · Tranh chấp · Thanh toán & Hoàn tiền mock)")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {

    private final BookingService bookingService;
    private final DisputeService disputeService;

    // --- Tab 1: Đơn thuê ---

    @GetMapping("/bookings")
    @Operation(summary = "Danh sách đơn thuê xe toàn hệ thống (lọc theo status, booking_code, địa chỉ)")
    public ResponseEntity<ApiResponse<List<AdminBookingDto>>> listBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) String keyword) {
        List<Booking> bookings = bookingService.listAdminBookings(status, keyword);
        List<AdminBookingDto> dtos = bookings.stream().map(this::mapBookingDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/bookings/{id}")
    @Operation(summary = "Chi tiết đơn thuê kèm các khoản phụ phí (charges)")
    public ResponseEntity<ApiResponse<AdminBookingDto>> getBookingDetail(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn thuê không tồn tại: " + id));
        return ResponseEntity.ok(ApiResponse.ok(mapBookingDto(booking)));
    }

    // --- Tab 2: Tranh chấp ---

    @GetMapping("/disputes")
    @Operation(summary = "Danh sách tranh chấp và khiếu nại (lọc theo status: OPEN, IN_REVIEW, RESOLVED, REJECTED)")
    public ResponseEntity<ApiResponse<List<AdminDisputeDto>>> listDisputes(
            @RequestParam(required = false) DisputeStatus status) {
        List<Dispute> disputes = disputeService.listDisputes(status);
        List<AdminDisputeDto> dtos = disputes.stream().map(this::mapDisputeDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PatchMapping("/disputes/{id}/resolve")
    @Operation(summary = "Xử lý và phân xử tranh chấp (nhập phương án giải quyết và trạng thái mới)")
    public ResponseEntity<ApiResponse<AdminDisputeDto>> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeResolveRequest request,
            @AuthenticationPrincipal SecurityUser securityUser) {
        Long adminId = (securityUser != null) ? securityUser.getUserId() : null;
        Dispute resolved = disputeService.resolveDispute(
                id, adminId, request.getStatus(), request.getDecision(), request.getResolution());
        return ResponseEntity.ok(ApiResponse.ok("Xử lý tranh chấp thành công", mapDisputeDto(resolved)));
    }

    // --- Tab 3: Thanh toán & Hoàn tiền mock ---

    @GetMapping("/payments")
    @Operation(summary = "Danh sách giao dịch thanh toán toàn hệ thống")
    public ResponseEntity<ApiResponse<List<AdminPaymentDto>>> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type) {
        List<PaymentTransaction> transactions = bookingService.listTransactions(status, type);
        List<AdminPaymentDto> dtos = transactions.stream().map(this::mapPaymentDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/refund")
    @Operation(summary = "Thực hiện hoàn tiền mô phỏng (Mock Refund) cho đơn thuê")
    public ResponseEntity<ApiResponse<AdminPaymentDto>> processRefund(
            @Valid @RequestBody RefundRequest request) {
        PaymentTransaction refund = bookingService.processMockRefund(request.getBookingId(), request.getAmount(), request.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Hoàn tiền thành công", mapPaymentDto(refund)));
    }

    private AdminBookingDto mapBookingDto(Booking b) {
        Renter r = b.getRenter();
        User ru = (r != null) ? r.getUser() : null;
        Vehicle v = b.getVehicle();
        Provider p = (v != null) ? v.getProvider() : null;
        Driver d = b.getDriver();
        User du = (d != null) ? d.getUser() : null;

        List<BookingCharge> charges = bookingService.getChargesForBooking(b.getId());
        List<AdminBookingDto.ChargeDto> chargeDtos = charges.stream()
                .map(c -> AdminBookingDto.ChargeDto.builder()
                        .id(c.getId())
                        .chargeType(c.getChargeType())
                        .description(c.getDescription())
                        .quantity(c.getQuantity())
                        .unitPrice(c.getUnitPrice())
                        .appliedRate(c.getAppliedRate())
                        .amount(c.getAmount())
                        .isWaived(c.isWaived())
                        .build())
                .toList();

        return AdminBookingDto.builder()
                .id(b.getId())
                .bookingCode(b.getBookingCode())
                .renterId(r != null ? r.getId() : null)
                .renterName(ru != null ? ru.getFullName() : null)
                .renterEmail(ru != null ? ru.getEmail() : null)
                .renterPhone(ru != null ? ru.getPhone() : null)
                .vehicleId(v != null ? v.getId() : null)
                .vehicleModel(v != null ? (v.getBrand() + " " + v.getModel()) : null)
                .vehicleLicensePlate(v != null ? v.getLicensePlate() : null)
                .providerId(p != null ? p.getId() : null)
                .providerDisplayName(p != null ? p.getDisplayName() : null)
                .driverId(d != null ? d.getId() : null)
                .driverName(du != null ? du.getFullName() : null)
                .status(b.getStatus())
                .pickupAddress(b.getPickupAddress())
                .pickupTime(b.getPickupTime())
                .passengerCount(b.getPassengerCount())
                .totalAmount(b.getTotalAmount())
                .actualStartAt(b.getActualStartAt())
                .actualEndAt(b.getActualEndAt())
                .renterConfirmedAt(b.getRenterConfirmedAt())
                .providerConfirmedAt(b.getProviderConfirmedAt())
                .cancelReason(b.getCancelReason())
                .cancelledAt(b.getCancelledAt())
                .charges(chargeDtos)
                .createdAt(b.getCreatedAt())
                .build();
    }

    private AdminDisputeDto mapDisputeDto(Dispute d) {
        Booking b = d.getBooking();
        BookingCharge c = d.getCharge();
        User raised = d.getRaisedBy();
        User handled = d.getHandledBy();

        return AdminDisputeDto.builder()
                .id(d.getId())
                .bookingId(b != null ? b.getId() : null)
                .bookingCode(b != null ? b.getBookingCode() : null)
                .chargeId(c != null ? c.getId() : null)
                .chargeDescription(c != null ? c.getDescription() : null)
                .chargeAmount(c != null ? c.getAmount() : null)
                .raisedById(raised != null ? raised.getId() : null)
                .raisedByName(raised != null ? raised.getFullName() : null)
                .raisedByEmail(raised != null ? raised.getEmail() : null)
                .handledById(handled != null ? handled.getId() : null)
                .handledByName(handled != null ? handled.getFullName() : null)
                .category(d.getCategory())
                .status(d.getStatus())
                .description(d.getDescription())
                .evidenceUrls(d.getEvidenceUrlList())
                .resolution(d.getResolution())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private AdminPaymentDto mapPaymentDto(PaymentTransaction p) {
        Booking b = p.getBooking();
        return AdminPaymentDto.builder()
                .id(p.getId())
                .bookingId(b != null ? b.getId() : null)
                .bookingCode(b != null ? b.getBookingCode() : null)
                .type(p.getType())
                .amount(p.getAmount())
                .direction(p.getDirection())
                .method(p.getMethod())
                .gatewayRef(p.getGatewayRef())
                .status(p.getStatus())
                .receiptUrl(p.getReceiptUrl())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
