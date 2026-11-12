package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.service.RefundEventService;

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
import com.fptufa26.ecomotion.finance.service.RefundMath;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
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
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.finance.service.CancellationClosureService;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.booking.service.implement.TripEndConfirmationService;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    @Override
    @Transactional(readOnly = true)
    public BigDecimal refundableAmount(Long currentUserId, String bookingCode) {
        Booking booking = resolveBooking(bookingCode);
        requireParticipantOrAdmin(requireUser(currentUserId), booking);
        var transactions = bookingPaymentLedgerService.listTransactions(booking.getId());
        BigDecimal received = transactions.stream().filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return received.subtract(reservedRefundAmount(booking.getId())).max(BigDecimal.ZERO);
    }

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final CancellationClosureService closure;
    private final RefundRequestRepository refundRequestRepository;
    private final BookingService bookingService;
    private final BookingPaymentLedgerService bookingPaymentLedgerService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final BookingNotificationService bookingNotificationService;
    private final RefundEventService refundEventService;
    private final org.springframework.beans.factory.ObjectProvider<TripEndConfirmationService> endConfirmations;
    private final org.springframework.beans.factory.ObjectProvider<PaymentRequestRepository> paymentRequests;

    private static final java.util.Set<BookingStatus> OFFSET_BLOCKED = java.util.Set.of(
            BookingStatus.AWAITING_FINAL_PAYMENT, BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.CANCELLATION_REVIEW);

    @Override
    @Transactional
    public RefundResponse offsetExcessRefund(Long currentUserId, Long refundRequestId) {
        RefundRequest candidate = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản hoàn #" + refundRequestId));
        Booking booking = bookingPaymentLedgerService.requireBookingForUpdate(candidate.getBooking().getId());
        if (!booking.getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Chỉ khách thuê của đơn mới được chọn cấn trừ");
        }
        if (booking.getPlan().getPlanType() == com.fptufa26.ecomotion.vehicle.enums.PlanType.YEARLY) throw new ConflictException("Gói năm hoàn tiền dư riêng, chưa hỗ trợ cấn trừ vào kỳ thuê hoặc quyết toán");
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundRequestId).orElseThrow();
        entityManager.refresh(refund, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!RefundMath.excessEvent(refund.getSourceEventId())) {
            throw new ConflictException("Chỉ khoản chuyển dư mới được cấn trừ vào quyết toán");
        }
        PaymentTransaction out = refund.getPaymentTransaction();
        if (refund.getStatus() != RefundStatus.PROCESSING || out == null || out.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Khoản hoàn đã được xử lý, không thể cấn trừ");
        }
        boolean finalIssued = paymentRequests.getObject().findByBooking_IdAndPaymentType(booking.getId(),
                PaymentRequestType.FINAL_BALANCE).isPresent();
        if (OFFSET_BLOCKED.contains(booking.getStatus()) || finalIssued) {
            throw new ConflictException("Đơn đã đến bước quyết toán; khoản chuyển dư cần được nhà xe hoàn lại");
        }
        Instant now = clock.instant();
        // Phần dư trở thành tiền đã trả cho đơn: tăng phần áp dụng của giao dịch gốc, huỷ lệnh hoàn.
        PaymentTransaction source = refund.getSourcePaymentTransaction();
        BigDecimal applied = source.getAppliedAmount() == null ? BigDecimal.ZERO : source.getAppliedAmount();
        source.setAppliedAmount(applied.add(refund.getAmount()).min(source.getAmount()));
        if (RefundMath.excess(source.getProcessingNote())) {
            source.setProcessingNote("Khách chọn cấn trừ khoản chuyển dư vào quyết toán");
        }
        bookingPaymentLedgerService.saveTransaction(source);
        out.setStatus(PaymentStatus.FAILED);
        out.setProcessingNote(RefundMath.EXCESS_PREFIX + ": khách chọn cấn trừ vào quyết toán");
        out.setUpdatedAt(now);
        bookingPaymentLedgerService.saveTransaction(out);
        refund.setStatus(RefundStatus.REJECTED);
        refund.setRejectionReason("Khách chọn cấn trừ vào quyết toán");
        refund.setUpdatedAt(now);
        bookingNotificationService.changed(booking, "REFUND_UPDATED");
        return toRefundResponse(refundRequestRepository.save(refund));
    }

    @Override
    @Transactional
    public RefundResponse createRefundRequest(Long currentUserId, CreateRefundRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0
                || request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 500) {
            throw new InvalidPaymentOperationException("Số tiền hoàn phải dương và lý do phải từ 1 đến 500 ký tự");
        }
        User user = requireUser(currentUserId);
        Booking booking = resolveBooking(request.bookingCode());
        booking = bookingPaymentLedgerService.requireBookingForUpdate(booking.getId());
        requireParticipantOrAdmin(user, booking);

        String eventId = "MANUAL:" + booking.getId() + ":"
                + request.amount().stripTrailingZeros().toPlainString().replace(".", "p") + ":"
                + Integer.toHexString((request.reason().trim() + "|" + java.util.Objects.toString(request.evidenceUrl(), "")).hashCode());
        List<RefundRequest> created = refundEventService.reserve(booking.getId(), eventId, request.amount(), request.reason().trim(), user);
        if (created.isEmpty()) {
            throw new InvalidPaymentOperationException("Đơn thuê không còn khoản tiền hợp lệ để hoàn trả");
        }
        created.forEach(refund -> refund.setEvidenceUrl(request.evidenceUrl()));
        return toRefundResponse(created.get(0));
    }

    @Override
    @Transactional
    public RefundResponse reviewRefundRequest(Long currentUserId, Long refundRequestId, ReviewRefundRequest request) {
        User reviewer = requireUser(currentUserId);
        RefundRequest candidate = refundRequestRepository.findById(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền #" + refundRequestId));
        requireProviderOrAdmin(reviewer, candidate.getBooking());
        bookingPaymentLedgerService.requireBookingForUpdate(candidate.getBooking().getId());
        entityManager.detach(candidate);
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu hoàn tiền #" + refundRequestId));
        entityManager.refresh(refund, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireProviderOrAdmin(reviewer, refund.getBooking());

        if ("APPROVE".equals(request.action() != null ? request.action().trim().toUpperCase(Locale.ROOT) : "")
                && refund.getStatus() == RefundStatus.PROCESSING
                && refund.getPaymentTransaction() != null
                && refund.getPaymentTransaction().getType() == PaymentType.REFUND) {
            return toRefundResponse(refund);
        }
        if (refund.getStatus() != RefundStatus.REQUESTED && refund.getStatus() != RefundStatus.UNDER_REVIEW
                && refund.getStatus() != RefundStatus.FAILED) {
            throw new InvalidPaymentOperationException("Yêu cầu hoàn tiền không còn ở trạng thái chờ xét duyệt");
        }

        String action = request.action() != null ? request.action().trim().toUpperCase(Locale.ROOT) : "";
        Instant now = clock.instant();

        if ("APPROVE".equals(action) || "CONTINUE".equals(action) || "MANUAL".equals(action)) {
            if ("CONTINUE".equals(action) || "MANUAL".equals(action)
                    || refund.getPaymentTransaction() != null && refund.getPaymentTransaction().getType() == PaymentType.REFUND
                    && Set.of(RefundStatus.UNDER_REVIEW, RefundStatus.FAILED).contains(refund.getStatus())) {
                return toRefundResponse(refundEventService.releaseForFollowUp(refund.getId(), reviewer, "MANUAL".equals(action)));
            }
            if (refund.getPaymentTransaction() != null && refund.getPaymentTransaction().getType() == PaymentType.REFUND) {
                refund.setApprovedBy(reviewer);
                refund.setStatus(RefundStatus.PROCESSING);
                refund.setUpdatedAt(now);
            } else {
            BigDecimal paid = bookingPaymentLedgerService.listTransactions(refund.getBooking().getId()).stream()
                    .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                    .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal committed = reservedRefundAmount(refund.getBooking().getId());
            if (committed.compareTo(paid) > 0)
                throw new ConflictException("Số tiền hoàn vượt khoản tiền còn có thể hoàn");
            PaymentTransaction refundTx = new PaymentTransaction();
            refundTx.setBooking(refund.getBooking());
            refundTx.setType(PaymentType.REFUND);
            refundTx.setDirection(PaymentDirection.OUT);
            refundTx.setMethod(PaymentMethod.BANK_TRANSFER);
            refundTx.setAmount(refund.getAmount());
            refundTx.setStatus(PaymentStatus.PENDING);
            refundTx.setCreatedAt(now);
            refundTx.setUpdatedAt(now);
            PaymentTransaction savedTx = bookingPaymentLedgerService.saveTransaction(refundTx);

            refund.setPaymentTransaction(savedTx);
            refund.setApprovedBy(reviewer);
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setUpdatedAt(now);
            }
        } else if ("REJECT".equals(action)) {
            if (refund.getBooking().getPlan().getPlanType() == com.fptufa26.ecomotion.vehicle.enums.PlanType.YEARLY
                    && RefundMath.excessEvent(refund.getSourceEventId())) throw new ConflictException("Không từ chối nghĩa vụ hoàn tiền chuyển dư đã ghi nhận; cần đối soát nếu thông tin sai");
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()
                    || request.rejectionReason().trim().length() > 500) {
                throw new InvalidPaymentOperationException("Lý do từ chối phải từ 1 đến 500 ký tự");
            }
            refund.setStatus(RefundStatus.REJECTED);
            refund.setApprovedBy(reviewer);
            refund.setRejectionReason(request.rejectionReason().trim());
            refund.setUpdatedAt(now);
        } else {
            throw new InvalidPaymentOperationException("Hành động duyệt không hợp lệ: " + action);
        }

        bookingNotificationService.changed(refund.getBooking(), "REFUND_UPDATED");
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
        if (requireUser(currentUserId).getRole() == Role.ADMIN)
            return refundRequestRepository.findAll().stream().map(this::toRefundResponse).toList();
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
        PaymentTransaction candidate = requireTransaction(transactionId);
        User user = requireUser(currentUserId);
        requireProviderOrAdmin(user, candidate.getBooking());
        bookingPaymentLedgerService.requireBookingForUpdate(candidate.getBooking().getId());
        PaymentTransaction transaction = bookingPaymentLedgerService.findTransactionByIdForUpdate(transactionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
        requireProviderOrAdmin(user, transaction.getBooking());
        requirePendingRefund(transaction);
        if (transaction.getMethod() == PaymentMethod.VNPAY) {
            throw new ConflictException("Hoàn qua VNPay không dùng minh chứng chuyển khoản");
        }
        RefundRequest refund = refundRequestRepository.findByPaymentTransaction_Id(transaction.getId()).orElse(null);
        requireRecipientAccount(refund);
        BigDecimal received = bookingPaymentLedgerService.listTransactions(transaction.getBooking().getId()).stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reservedRefundAmount(transaction.getBooking().getId()).compareTo(received) > 0) {
            throw new ConflictException("Tổng nghĩa vụ hoàn vượt số tiền thực nhận; cần đối soát trước khi xác nhận");
        }
        validateProof(proof);

        String reference = normalizeReference(bankReference);
        if (reference != null && bookingPaymentLedgerService.gatewayReferenceExists(reference)) {
            throw new ConflictException("Mã giao dịch ngân hàng đã được sử dụng");
        }
        String normalizedNote = normalizeNote(note);
        String storedPath;
        try {
            storedPath = fileStorageService.store(
                    "refund-proof", currentUserId, proof.getOriginalFilename(), proof.getBytes(), proof.getContentType());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc tệp minh chứng hoàn tiền", exception);
        }

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) fileStorageService.delete(storedPath);
                        }
                    });
        }
        Instant now = clock.instant();
        transaction.setReceiptUrl(storedPath);
        transaction.setGatewayRef(reference);
        // Giữ tiền tố EXCESS (tiền khách chuyển dư) để khoản hoàn không bị tính như hoàn tiền thuê.
        String previousNote = transaction.getProcessingNote();
        transaction.setProcessingNote(RefundMath.excess(previousNote)
                ? (RefundMath.EXCESS_PREFIX + (normalizedNote == null ? "" : ": " + normalizedNote))
                : normalizedNote);
        transaction.setConfirmedBy(user);
        transaction.setVerificationMethod(user.getRole() == Role.ADMIN
                ? PaymentVerificationMethod.ADMIN_RECONCILED
                : PaymentVerificationMethod.PROVIDER_CONFIRMED);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(now);
        bookingPaymentLedgerService.saveTransaction(transaction);

        if (refund != null) {
            refund.setStatus(RefundStatus.REFUNDED);
            if (refund.getReceiptStatus() == null) {
                refund.setReceiptStatus("WAITING");
                refund.setReceiptWaitingAt(now);
                refund.setReceiptDeadline(now.plus(java.time.Duration.ofHours(72)));
            }
            refund.setGatewayRefundRef(transaction.getGatewayRef());
            refund.setUpdatedAt(now);
            refundRequestRepository.save(refund);
        }

        Booking booking = transaction.getBooking();
        closure.reconcile(booking.getId());
        endConfirmations.getObject().completeIfReady(booking);
        bookingNotificationService.changed(transaction.getBooking(), "REFUND_UPDATED");
        return toProofResponse(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentEvidenceFileResponse getProof(Long currentUserId, Long transactionId) {
        PaymentTransaction transaction = bookingPaymentLedgerService.findTransactionById(transactionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
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

    /**
     * A manual bank transfer can only be confirmed once the renter has supplied a destination account.
     * Legacy refund transactions without a linked request cannot be updated by the renter, so they keep
     * the previous behaviour.
     */
    private void requireRecipientAccount(RefundRequest refund) {
        if (refund == null) {
            return;
        }
        if (isBlank(refund.getRecipientBankName()) || isBlank(refund.getRecipientAccountHolder())
                || isBlank(refund.getRecipientAccountNumber())) {
            throw new ConflictException(
                    "Khách thuê chưa cung cấp tài khoản nhận hoàn. Yêu cầu khách cập nhật tài khoản nhận tiền trước khi xác nhận chuyển khoản hoàn.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 100) throw new IllegalArgumentException("Mã giao dịch tối đa 100 ký tự");
        return normalized;
    }

    private BigDecimal reservedRefundAmount(Long bookingId) {
        List<RefundRequest> requests = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        Set<Long> linkedTransactions = requests.stream()
                .filter(r -> r.getPaymentTransaction() != null && r.getPaymentTransaction().getType() == PaymentType.REFUND)
                .map(r -> r.getPaymentTransaction().getId()).collect(java.util.stream.Collectors.toSet());
        BigDecimal unlinkedRefunds = bookingPaymentLedgerService.listTransactions(bookingId).stream()
                .filter(t -> (t.getType() == PaymentType.REFUND || t.getType() == PaymentType.SECURITY_DEPOSIT_REFUND) && t.getDirection() == PaymentDirection.OUT
                        && (t.getStatus() == PaymentStatus.PENDING || t.getStatus() == PaymentStatus.PAID)
                        && !linkedTransactions.contains(t.getId()))
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return refundRequestRepository.sumCommittedRefundsForBooking(bookingId).add(unlinkedRefunds);
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
                r.getUpdatedAt(),
                r.getPaymentTransaction() != null ? r.getPaymentTransaction().getMethod() : null,
                r.getRecipientBankName(), r.getRecipientAccountHolder(), r.getRecipientAccountNumber(),
                r.getReceiptStatus(), r.getReceiptWaitingAt(), r.getReceiptDeadline(),
                r.getReceiptConfirmedAt(), r.getReceiptConfirmedBy(), r.getReceiptReportedAt(), r.getReceiptAutoConfirmedAt(),
                r.getPaymentTransaction() != null && r.getPaymentTransaction().getReceiptUrl() != null
                        && !r.getPaymentTransaction().getReceiptUrl().isBlank(),
                r.getVersion(),
                RefundMath.excessEvent(r.getSourceEventId())
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
