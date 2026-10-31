package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.finance.dto.request.CreateRefundRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReviewRefundRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundResponse;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.RefundService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final RefundRequestRepository refundRequestRepository;
    private final BookingService bookingService;
    private final BookingPaymentLedgerService bookingPaymentLedgerService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    @Override
    @Transactional
    public RefundResponse createRefundRequest(Long currentUserId, CreateRefundRequest request) {
        User user = requireUser(currentUserId);
        Booking booking = resolveBooking(request.bookingCode());
        requireParticipantOrAdmin(user, booking);

        List<PaymentTransaction> incomingPaid = bookingPaymentLedgerService.listTransactions(booking.getId()).stream()
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
        Instant now = clock.instant();

        RefundRequest refund = new RefundRequest();
        refund.setBooking(booking);
        refund.setPaymentTransaction(originalTx);
        refund.setAmount(request.amount());
        refund.setReason(request.reason());
        refund.setEvidenceUrl(request.evidenceUrl());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setRequestedBy(user);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        return toRefundResponse(refundRequestRepository.save(refund));
    }

    @Override
    @Transactional
    public RefundResponse reviewRefundRequest(Long currentUserId, Long refundRequestId, ReviewRefundRequest request) {
        User reviewer = requireUser(currentUserId);
        RefundRequest refund = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền #" + refundRequestId));
        requireProviderOrAdmin(reviewer, refund.getBooking());

        if (refund.getStatus() != RefundStatus.REQUESTED && refund.getStatus() != RefundStatus.UNDER_REVIEW) {
            throw new InvalidPaymentOperationException("Yêu cầu hoàn tiền không còn ở trạng thái chờ xét duyệt");
        }

        String action = request.action() != null ? request.action().trim().toUpperCase(Locale.ROOT) : "";
        Instant now = clock.instant();

        if ("APPROVE".equals(action)) {
            PaymentTransaction refundTx = new PaymentTransaction();
            refundTx.setBooking(refund.getBooking());
            refundTx.setType(PaymentType.REFUND);
            refundTx.setDirection(PaymentDirection.OUT);
            refundTx.setMethod(refund.getPaymentTransaction() != null
                    ? refund.getPaymentTransaction().getMethod()
                    : PaymentMethod.BANK_TRANSFER);
            refundTx.setAmount(refund.getAmount());
            refundTx.setStatus(PaymentStatus.PENDING);
            refundTx.setCreatedAt(now);
            refundTx.setUpdatedAt(now);
            PaymentTransaction savedTx = bookingPaymentLedgerService.saveTransaction(refundTx);

            refund.setPaymentTransaction(savedTx);
            refund.setApprovedBy(reviewer);
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setUpdatedAt(now);
        } else if ("REJECT".equals(action)) {
            refund.setStatus(RefundStatus.REJECTED);
            refund.setApprovedBy(reviewer);
            refund.setRejectionReason(request.rejectionReason());
            refund.setUpdatedAt(now);
        } else {
            throw new InvalidPaymentOperationException("Hành động duyệt không hợp lệ: " + action);
        }

        return toRefundResponse(refundRequestRepository.save(refund));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> listRefundsByBooking(Long currentUserId, String bookingCodeOrId) {
        User user = requireUser(currentUserId);
        Booking booking = resolveBooking(bookingCodeOrId);
        requireParticipantOrAdmin(user, booking);
        return refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .map(this::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> listRefundsForProvider(Long currentUserId) {
        Provider provider = userService.findProviderByUserId(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng không phải là nhà cung cấp hợp lệ"));
        return refundRequestRepository.findByBooking_Vehicle_Provider_IdOrderByCreatedAtDesc(provider.getId()).stream()
                .map(this::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional
    public RefundProofResponse completeRefund(Long currentUserId, Long transactionId, MultipartFile proof,
                                              String bankReference, String note) {
        PaymentTransaction transaction = requireTransaction(transactionId);
        User user = requireUser(currentUserId);
        requireProviderOrAdmin(user, transaction.getBooking());
        requirePendingRefund(transaction);
        validateProof(proof);

        String storedPath;
        try {
            storedPath = fileStorageService.store(
                    "refund-proof", currentUserId, proof.getOriginalFilename(), proof.getBytes(), proof.getContentType());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc tệp minh chứng hoàn tiền", exception);
        }

        Instant now = clock.instant();
        transaction.setReceiptUrl(storedPath);
        transaction.setGatewayRef(normalizeReference(bankReference));
        transaction.setProcessingNote(normalizeNote(note));
        transaction.setConfirmedBy(user);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(now);
        bookingPaymentLedgerService.saveTransaction(transaction);

        refundRequestRepository.findByPaymentTransaction_Id(transaction.getId()).ifPresent(refund -> {
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setGatewayRefundRef(transaction.getGatewayRef());
            refund.setUpdatedAt(now);
            refundRequestRepository.save(refund);
        });

        Booking booking = transaction.getBooking();
        if (booking.getStatus() == BookingStatus.CANCELLATION_REVIEW) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingPaymentLedgerService.saveBooking(booking);
        }
        return toProofResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentEvidenceFileResponse getProof(Long currentUserId, Long transactionId) {
        PaymentTransaction transaction = requireTransaction(transactionId);
        User user = requireUser(currentUserId);
        requireParticipantOrAdmin(user, transaction.getBooking());
        if (transaction.getReceiptUrl() == null || transaction.getReceiptUrl().isBlank()) {
            throw new NotFoundException("Chưa có minh chứng hoàn tiền");
        }
        String path = transaction.getReceiptUrl();
        return new PaymentEvidenceFileResponse(
                fileStorageService.read(path), contentType(path), path.substring(path.lastIndexOf('/') + 1));
    }

    private PaymentTransaction requireTransaction(Long transactionId) {
        return bookingPaymentLedgerService.findTransactionById(transactionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch hoàn tiền"));
    }

    private User requireUser(Long currentUserId) {
        return userService.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
    }

    private Booking resolveBooking(String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new ResourceNotFoundException("Không tìm thấy đơn thuê");
        }
        String token = bookingCodeOrId.trim();
        return bookingService.findByBookingCode(token)
                .or(() -> {
                    try {
                        return bookingService.getBookingById(Long.parseLong(token));
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê: " + token));
    }

    private void requireProviderOrAdmin(User user, Booking booking) {
        boolean providerOwner = user.getRole() == Role.PROVIDER
                && booking.getVehicle().getProvider().getUser().getId().equals(user.getId());
        if (user.getRole() != Role.ADMIN && !providerOwner) {
            throw new AccessDeniedException("Bạn không có quyền xử lý hoàn tiền này");
        }
    }

    private void requireParticipantOrAdmin(User user, Booking booking) {
        boolean renterOwner = booking.getRenter().getUser().getId().equals(user.getId());
        boolean providerOwner = booking.getVehicle().getProvider().getUser().getId().equals(user.getId());
        if (user.getRole() != Role.ADMIN && !renterOwner && !providerOwner) {
            throw new AccessDeniedException("Bạn không có quyền xem minh chứng này");
        }
    }

    private void requirePendingRefund(PaymentTransaction transaction) {
        if (transaction.getType() != PaymentType.REFUND || transaction.getDirection() != PaymentDirection.OUT) {
            throw new ConflictException("Giao dịch này không phải yêu cầu hoàn tiền");
        }
        if (transaction.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Yêu cầu hoàn tiền đã được xử lý");
        }
    }

    private void validateProof(MultipartFile proof) {
        if (proof == null || proof.isEmpty()) {
            throw new IllegalArgumentException("Minh chứng hoàn tiền là bắt buộc");
        }
        if (proof.getSize() > MAX_PROOF_BYTES || !ALLOWED_TYPES.contains(proof.getContentType())) {
            throw new IllegalArgumentException("Minh chứng phải là JPG, PNG, WebP hoặc PDF tối đa 5 MB");
        }
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mã giao dịch hoàn tiền là bắt buộc");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Ghi chú không được vượt quá 500 ký tự");
        }
        return normalized;
    }

    private RefundProofResponse toProofResponse(PaymentTransaction transaction) {
        return new RefundProofResponse(
                transaction.getId(), transaction.getBooking().getBookingCode(), transaction.getAmount(),
                transaction.getStatus(), transaction.getGatewayRef(), transaction.getProcessingNote(),
                "/api/v1/payment-transactions/" + transaction.getId() + "/refund-proof", transaction.getPaidAt());
    }

    private RefundResponse toRefundResponse(RefundRequest r) {
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

    private String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "image/jpeg";
    }
}
