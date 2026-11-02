package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.*;
import com.fptufa26.ecomotion.finance.service.gateway.VietQrPaymentGatewayStrategy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VietQrPaymentServiceImpl implements VietQrPaymentService {
    private final PaymentRequestRepository requests;
    private final PaymentRequestService payments;
    private final BookingPaymentLedgerService ledger;
    private final VietQrPaymentGatewayStrategy gateway;
    private final EntityManager em;
    private final Clock clock;
    private final com.fptufa26.ecomotion.dispute.service.DisputeQueryService disputes;
    private final com.fptufa26.ecomotion.user.service.UserService users;
    private final com.fptufa26.ecomotion.finance.repository.VietQrCallbackRepository callbackRepository;

    @Override
    public org.springframework.data.domain.Page<com.fptufa26.ecomotion.finance.dto.response.VietQrCallbackResponse> callbacks(Long userId, int page) {
        var user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Không có quyền truy cập"));
        if (user.getStatus() != com.fptufa26.ecomotion.user.enums.UserStatus.ACTIVE)
            throw new AccessDeniedException("Tài khoản không hoạt động");
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20,
                org.springframework.data.domain.Sort.by("receivedAt").descending());
        var callbacks = user.getRole() == com.fptufa26.ecomotion.user.enums.Role.ADMIN
                ? callbackRepository.findAll(pageable) : callbackRepository.findProviderCallbacks(userId, pageable);
        return callbacks.map(c -> new com.fptufa26.ecomotion.finance.dto.response.VietQrCallbackResponse(
                c.getTransactionId(), c.getOrderId(), c.getPaymentRequestId(), c.getOutcome(), c.getReceivedAt()));
    }

    @Override
    @Transactional
    public PaymentRequestResponse create(Long paymentRequestId, Long userId) {
        var candidate = requests.findById(paymentRequestId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var booking = ledger.requireBookingForUpdate(candidate.getBooking().getId());
        em.detach(candidate);
        var request = requests.findByIdForUpdate(paymentRequestId).orElseThrow();
        em.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        if (!booking.getRenter().getUser().getId().equals(userId)) throw new AccessDeniedException("Không có quyền tạo QR");
        payments.getPaymentRequestDetail(request.getId(), userId);
        if (request.getStatus() != PaymentRequestStatus.PENDING || request.getPaidAmount().signum() != 0
                || request.getExpiresAt() == null || !request.getExpiresAt().isAfter(clock.instant())
                || booking.getCancelledAt() != null || disputes.hasActiveDispute(booking.getId())
                || (request.getPaymentType() == PaymentRequestType.DEPOSIT
                    ? booking.getStatus() != BookingStatus.AWAITING_DEPOSIT
                    : booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT))
            throw new ResponseStatusException(CONFLICT, "Yêu cầu thanh toán đã kết thúc hoặc đang cần đối soát");
        PaymentType type = request.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT;
        if (ledger.listTransactions(booking.getId()).stream().anyMatch(t -> t.getType() == type
                && t.getDirection() == PaymentDirection.IN && (t.getStatus() == PaymentStatus.PAID || t.getStatus() == PaymentStatus.PENDING)))
            throw new ResponseStatusException(CONFLICT, "Nghĩa vụ thanh toán đã có giao dịch; vui lòng chờ đối soát");
        if (request.isVietQrManaged()) return payments.getPaymentRequestDetail(request.getId(), userId);
        var account = request.getReceivingBankAccount();
        if (account == null || account.getStatus() != BankAccountStatus.ACTIVE || account.getVietQrLinkedAt() == null
                || !account.getAccountNumber().equals(request.getAccountNumberSnapshot())
                || !account.getBankCode().equals(request.getBankCodeSnapshot())
                || !account.getAccountHolderName().equals(request.getAccountHolderSnapshot())
                || !account.getProvider().getId().equals(booking.getVehicle().getProvider().getId()))
            throw new ResponseStatusException(CONFLICT, "Tài khoản nhận chưa liên kết đồng bộ VietQR hoặc không khớp bản chốt");
        if (request.getPaymentCode() == null || !request.getPaymentCode().matches("VQ[0-9]{6}"))
            throw new ResponseStatusException(CONFLICT, "Mã thanh toán không hợp lệ; cần cấp lại yêu cầu");
        if (request.getPaymentType() == PaymentRequestType.DEPOSIT
                && !payments.hasValidVietQrHold(booking.getId()))
            throw new ResponseStatusException(CONFLICT, "Hold đã hết hạn hoặc lịch không còn hợp lệ");
        if (request.getPaymentType() == PaymentRequestType.FINAL_BALANCE
                && (booking.getActualEndAt() == null || booking.getProviderConfirmedAt() == null || booking.getRenterConfirmedAt() == null
                    || payments.calculateRemainingAmount(booking.getId()).compareTo(request.getAmount()) != 0))
            throw new ResponseStatusException(CONFLICT, "Booking chưa đủ điều kiện thanh toán cuối");
        var qr = gateway.generate(request.getPaymentCode(), request.getBankCodeSnapshot(), request.getAccountNumberSnapshot(),
                request.getAccountHolderSnapshot(), request.getAmount());
        request.setVietQrManaged(true);
        request.setVietQrOrderId(qr.orderId());
        request.setTransferReference(qr.content());
        request.setVietQrPayload(qr.qrCode());
        request.setVietQrLink(qr.qrLink());
        return payments.getPaymentRequestDetail(request.getId(), userId);
    }

    @Override
    public PaymentRequestResponse status(String paymentCode, Long userId) {
        var request = requests.findByPaymentCode(paymentCode).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        return payments.getPaymentRequestDetail(request.getId(), userId);
    }
}
