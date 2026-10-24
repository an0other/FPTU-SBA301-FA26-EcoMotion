package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.RefundService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final BookingRepository bookingRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final Clock clock;

    @Override
    @Transactional
    public RefundResponse createRefundRequest(Long currentUserId, CreateRefundRequest request) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Booking booking = bookingRepository.findByBookingCode(request.bookingCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê: " + request.bookingCode()));

        boolean isRenter = booking.getRenter().getUser().getId().equals(currentUserId);
        boolean isProvider = booking.getVehicle().getProvider().getUser().getId().equals(currentUserId);
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isRenter && !isProvider && !isAdmin) {
            throw new AccessDeniedException("Bạn không có quyền yêu cầu hoàn tiền cho đơn thuê này");
        }

        List<PaymentTransaction> incomingPaid = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                .filter(tx -> tx.getStatus() == PaymentStatus.PAID && tx.getDirection() == PaymentDirection.IN)
                .toList();

        BigDecimal totalPaid = incomingPaid.stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal committedRefunds = refundRequestRepository.sumCommittedRefundsForBooking(booking.getId());
        BigDecimal maxRefundable = totalPaid.subtract(committedRefunds);

        if (maxRefundable.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentOperationException("Đơn thuê không còn khoản tiền hợp lệ để hoàn trả");
        }

        if (request.amount().compareTo(maxRefundable) > 0) {
            throw new InvalidPaymentOperationException("Số tiền yêu cầu hoàn (" + request.amount()
                    + ") vượt quá số tiền tối đa có thể hoàn (" + maxRefundable + ")");
        }

        PaymentTransaction originalTx = incomingPaid.isEmpty() ? null : incomingPaid.get(0);

        RefundRequest refund = new RefundRequest();
        refund.setBooking(booking);
        refund.setPaymentTransaction(originalTx);
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setEvidenceUrl(request.evidenceUrl());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setRequestedBy(user);
        refund.setCreatedAt(clock.instant());
        refund.setUpdatedAt(clock.instant());

        RefundRequest saved = refundRequestRepository.save(refund);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RefundResponse reviewRefundRequest(Long currentUserId, Long refundRequestId, ReviewRefundRequest request) {
        User reviewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        RefundRequest refund = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền #" + refundRequestId));

        Booking booking = refund.getBooking();
        boolean isProvider = booking.getVehicle().getProvider().getUser().getId().equals(currentUserId);
        boolean isAdmin = reviewer.getRole() == Role.ADMIN;

        if (!isProvider && !isAdmin) {
            throw new AccessDeniedException("Chỉ nhà cung cấp hoặc quản trị viên mới có quyền duyệt hoàn tiền");
        }

        if (refund.getStatus() != RefundStatus.REQUESTED && refund.getStatus() != RefundStatus.UNDER_REVIEW) {
            throw new InvalidPaymentOperationException("Yêu cầu hoàn tiền không còn ở trạng thái chờ xét duyệt");
        }

        String action = request.action() != null ? request.action().trim().toUpperCase() : "";
        Instant now = clock.instant();

        if ("APPROVE".equals(action)) {
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setApprovedBy(reviewer);

            PaymentTransaction refundTx = new PaymentTransaction();
            refundTx.setBooking(booking);
            refundTx.setType(PaymentType.REFUND);
            refundTx.setDirection(PaymentDirection.OUT);
            refundTx.setMethod(refund.getPaymentTransaction() != null ? refund.getPaymentTransaction().getMethod() : PaymentMethod.VNPAY);
            refundTx.setAmount(refund.getAmount());
            refundTx.setStatus(PaymentStatus.PAID);
            refundTx.setPaidAt(now);

            String gatewayRef = "REFUND-" + refund.getId() + "-" + now.toEpochMilli();
            refundTx.setGatewayRef(gatewayRef);
            refundTx.setConfirmedBy(reviewer);
            refundTx.setCreatedAt(now);
            refundTx.setUpdatedAt(now);
            PaymentTransaction savedTx = paymentTransactionRepository.save(refundTx);

            refund.setPaymentTransaction(savedTx);
            refund.setGatewayRefundRef(gatewayRef);
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setUpdatedAt(now);
        } else if ("REJECT".equals(action)) {
            refund.setStatus(RefundStatus.REJECTED);
            refund.setApprovedBy(reviewer);
            refund.setRejectionReason(request.rejectionReason());
            refund.setUpdatedAt(now);
        } else {
            throw new InvalidPaymentOperationException("Hành động duyệt không hợp lệ: " + action);
        }

        RefundRequest saved = refundRequestRepository.save(refund);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> listRefundsByBooking(Long currentUserId, String bookingCodeOrId) {
        Booking booking = resolveBooking(bookingCodeOrId);
        return refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> listRefundsForProvider(Long currentUserId) {
        Provider provider = providerRepository.findByUser_Id(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng không phải là nhà cung cấp hợp lệ"));

        return refundRequestRepository.findByBooking_Vehicle_Provider_IdOrderByCreatedAtDesc(provider.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private Booking resolveBooking(String bookingCodeOrId) {
        try {
            Long id = Long.parseLong(bookingCodeOrId);
            return bookingRepository.findById(id)
                    .or(() -> bookingRepository.findByBookingCode(bookingCodeOrId))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê: " + bookingCodeOrId));
        } catch (NumberFormatException e) {
            return bookingRepository.findByBookingCode(bookingCodeOrId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê: " + bookingCodeOrId));
        }
    }

    private RefundResponse toResponse(RefundRequest r) {
        return new RefundResponse(
                r.getId(),
                r.getBooking().getId(),
                r.getBooking().getBookingCode(),
                r.getPaymentTransaction() != null ? r.getPaymentTransaction().getId() : null,
                r.getAmount(),
                r.getReason(),
                r.getEvidenceUrl(),
                r.getStatus(),
                r.getRequestedBy() != null ? r.getRequestedBy().getId() : null,
                r.getRequestedBy() != null ? r.getRequestedBy().getFullName() : null,
                r.getApprovedBy() != null ? r.getApprovedBy().getId() : null,
                r.getApprovedBy() != null ? r.getApprovedBy().getFullName() : null,
                r.getRejectionReason(),
                r.getGatewayRefundRef(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
