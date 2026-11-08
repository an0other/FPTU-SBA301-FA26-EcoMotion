package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeConfirmationStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.service.SettlementService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final BookingQueryService bookingQueryService;
    private final DisputeService disputeService;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public RenterSettlementResponse getSettlementDetails(Long currentUserId, String bookingCodeOrId) {
        Booking booking = resolveBooking(bookingCodeOrId);
        validateAccessToBooking(booking, currentUserId);

        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingQueryService.findChargesByBookingIdOrderByCreatedAtAsc(booking.getId());
        List<BookingChargeLineResponse> chargeLines = charges.stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.DEPOSIT)
                .map(c -> new BookingChargeLineResponse(
                        c.getId(),
                        c.getChargeType(),
                        c.getDescription(),
                        c.getQuantity(),
                        c.getUnitPrice(),
                        c.getAppliedRate(),
                        c.getAmount(),
                        c.isWaived(),
                        c.isWaived() ? BigDecimal.ZERO : c.getAmount(),
                        c.getConfirmationStatus(),
                        c.getEvidenceUrl()
                ))
                .toList();

        BigDecimal extraCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getConfirmationStatus() == ChargeConfirmationStatus.ACCEPTED
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL
                        && c.getChargeType() != ChargeType.WEEKEND)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalObligation = baseRental.add(extraCharges);

        List<PaymentTransaction> transactions = bookingQueryService.findTransactionsByBookingId(booking.getId());
        BigDecimal depositPaid = transactions.stream()
                .filter(t -> isInitialPayment(t.getType()) && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(t -> t.getAppliedAmount() == null ? BigDecimal.ZERO : t.getAppliedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal otherPaid = transactions.stream()
                .filter(t -> !isInitialPayment(t.getType()) && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(t -> t.getAppliedAmount() == null ? BigDecimal.ZERO : t.getAppliedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = depositPaid.add(otherPaid);

        BigDecimal refunds = transactions.stream()
                .filter(t -> t.getType() == PaymentType.REFUND && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPaid = totalPaid.subtract(refunds);
        BigDecimal netBalance = totalObligation.subtract(netPaid);
        BigDecimal finalPayable = netBalance.signum() > 0 ? netBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal overpayment = netBalance.signum() < 0 ? netBalance.abs().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        boolean hasDispute = disputeService.hasActiveDispute(booking.getId());
        boolean isSettled = booking.getStatus() == BookingStatus.COMPLETED
                || transactions.stream().anyMatch(t -> t.getType() == PaymentType.SETTLEMENT && t.getStatus() == PaymentStatus.PAID);
        boolean eligibleForPayment = booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT && !isSettled && finalPayable.signum() > 0 && !hasDispute;

        String message;
        if (isSettled) {
            message = "Đơn thuê đã hoàn tất quyết toán.";
        } else if (hasDispute) {
            message = "Đơn thuê đang có khiếu nại tranh chấp, tạm thời chưa thể quyết toán.";
        } else if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
            message = "Đơn thuê chưa ở trạng thái chờ thanh toán quyết toán.";
        } else if (finalPayable.signum() == 0) {
            message = "Số dư thanh toán bằng 0 ₫. Bạn không cần thanh toán thêm.";
        } else {
            message = "Vui lòng chuyển khoản số dư theo thông tin nhà cung cấp để hoàn tất đơn thuê.";
        }

        return new RenterSettlementResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                baseRental,
                chargeLines,
                extraCharges,
                depositPaid,
                otherPaid,
                totalPaid,
                refunds,
                finalPayable,
                overpayment,
                eligibleForPayment,
                isSettled,
                message
        );
    }

    private boolean isInitialPayment(PaymentType type) {
        return type == PaymentType.DEPOSIT_PAY || type == PaymentType.UPFRONT_PAY;
    }

    private Booking resolveBooking(String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new ResourceNotFoundException("Mã đơn thuê không được để trống");
        }
        Optional<Booking> opt = bookingQueryService.findBookingByCode(bookingCodeOrId.trim());
        if (opt.isEmpty()) {
            try {
                Long id = Long.parseLong(bookingCodeOrId.trim());
                opt = bookingQueryService.findBookingById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        return opt.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê với mã: " + bookingCodeOrId));
    }

    private void validateAccessToBooking(Booking booking, Long currentUserId) {
        if (currentUserId == null) throw new AccessDeniedException("Vui lòng đăng nhập để tiếp tục");
        if (booking.getRenter().getUser().getId().equals(currentUserId)) return;
        User user = userService.findById(currentUserId).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) return;
        if (user != null && booking.getVehicle().getProvider().getUser().getId().equals(currentUserId)) return;
        throw new AccessDeniedException("Bạn không có quyền truy cập thông tin quyết toán của đơn thuê này");
    }
}
