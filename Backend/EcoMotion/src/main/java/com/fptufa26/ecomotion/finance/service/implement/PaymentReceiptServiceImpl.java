package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.finance.dto.response.PaymentReceiptResponse;
import com.fptufa26.ecomotion.finance.enums.PaymentReceiptStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.PaymentReceiptService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import java.util.Comparator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentReceiptServiceImpl implements PaymentReceiptService {
    private final BookingPaymentLedgerService ledger;
    private final PaymentRequestRepository paymentRequests;
    private final UserService users;

    @Override
    public PaymentReceiptResponse getReceipt(Long currentUserId, String reference) {
        if (currentUserId == null) throw new AccessDeniedException("Vui lòng đăng nhập để xem biên lai");
        User user = users.findById(currentUserId).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không hoạt động"));
        if (reference == null || reference.isBlank() || reference.length() > 100) throw notFound();
        PaymentTransaction payment = ledger.findTransactionByReference(reference.trim()).orElseGet(() -> {
            var request = paymentRequests.findByPaymentCode(reference.trim()).orElseThrow(this::notFound);
            requireAccess(user, request.getBooking());
            return ledger.listTransactions(request.getBooking().getId()).stream()
                    .filter(t -> t.getPaymentRequest() != null && t.getPaymentRequest().getId().equals(request.getId()))
                    .filter(t -> t.getDirection() == PaymentDirection.IN)
                    .max(Comparator.comparing(PaymentTransaction::getId)).orElseThrow(this::notFound);
        });
        requireAccess(user, payment.getBooking());
        if (payment.getDirection() != PaymentDirection.IN || !Set.of(PaymentType.DEPOSIT_PAY,
                PaymentType.UPFRONT_PAY, PaymentType.SETTLEMENT).contains(payment.getType())) throw notFound();
        PaymentReceiptStatus status = PaymentReceiptStatus.PENDING_VERIFICATION;
        if (payment.getStatus() == PaymentStatus.FAILED) status = PaymentReceiptStatus.FAILED;
        if (payment.getStatus() == PaymentStatus.PAID) {
            boolean verified = payment.getMethod() == PaymentMethod.VNPAY
                    ? payment.getVerificationMethod() == PaymentVerificationMethod.GATEWAY_VERIFIED
                    : payment.getVerificationMethod() != null && Set.of(PaymentVerificationMethod.PROVIDER_CONFIRMED, PaymentVerificationMethod.ADMIN_RECONCILED)
                        .contains(payment.getVerificationMethod());
            boolean reconciliation = payment.getProcessingNote() != null
                    && payment.getProcessingNote().startsWith("RECONCILIATION_REQUIRED");
            status = verified && !reconciliation && payment.getPaidAt() != null
                    && payment.getAppliedAmount() != null && payment.getAppliedAmount().signum() > 0
                    ? PaymentReceiptStatus.SUCCESS : PaymentReceiptStatus.RECONCILIATION_REQUIRED;
        }
        return new PaymentReceiptResponse(status, payment.getBooking().getBookingCode(), payment.getTransactionCode(),
                payment.getVnpTransactionNo(), payment.getAmount(), payment.getMethod(), payment.getVerificationMethod(), payment.getPaidAt());
    }

    private void requireAccess(User user, Booking booking) {
        if (user.getRole() != Role.ADMIN && !booking.getRenter().getUser().getId().equals(user.getId())
                && !booking.getVehicle().getProvider().getUser().getId().equals(user.getId())) throw notFound();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy biên lai thanh toán hoặc bạn không có quyền truy cập");
    }
}
