package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.RefundProofResponse;
import com.fptufa26.ecomotion.finance.service.RefundService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final long MAX_PROOF_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final BookingPaymentLedgerService bookingPaymentLedgerService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

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

        transaction.setReceiptUrl(storedPath);
        transaction.setGatewayRef(normalizeReference(bankReference));
        transaction.setProcessingNote(normalizeNote(note));
        transaction.setConfirmedBy(user);
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setPaidAt(Instant.now());
        bookingPaymentLedgerService.saveTransaction(transaction);

        Booking booking = transaction.getBooking();
        if (booking.getStatus() == BookingStatus.CANCELLATION_REVIEW) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingPaymentLedgerService.saveBooking(booking);
        }
        return toResponse(transaction);
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

    private void requireProviderOrAdmin(User user, Booking booking) {
        boolean providerOwner = user.getRole() == Role.PROVIDER
                && booking.getVehicle().getProvider().getUser().getId().equals(user.getId());
        if (user.getRole() != Role.ADMIN && !providerOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xử lý hoàn tiền này");
        }
    }

    private void requireParticipantOrAdmin(User user, Booking booking) {
        boolean renterOwner = booking.getRenter().getUser().getId().equals(user.getId());
        boolean providerOwner = booking.getVehicle().getProvider().getUser().getId().equals(user.getId());
        if (user.getRole() != Role.ADMIN && !renterOwner && !providerOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem minh chứng này");
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
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Ghi chú không được vượt quá 500 ký tự");
        }
        return normalized;
    }

    private RefundProofResponse toResponse(PaymentTransaction transaction) {
        return new RefundProofResponse(
                transaction.getId(), transaction.getBooking().getBookingCode(), transaction.getAmount(),
                transaction.getStatus(), transaction.getGatewayRef(), transaction.getProcessingNote(),
                "/api/v1/payment-transactions/" + transaction.getId() + "/refund-proof", transaction.getPaidAt());
    }

    private String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }
}
