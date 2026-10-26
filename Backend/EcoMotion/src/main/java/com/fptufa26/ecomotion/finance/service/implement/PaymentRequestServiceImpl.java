package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportIssueRequest;
import com.fptufa26.ecomotion.finance.dto.request.SimulatePaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.SubmitProofRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceFileResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.dto.response.SimulatePaymentResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.enums.SupportedBank;
import com.fptufa26.ecomotion.finance.exception.BankAccountConflictException;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.exception.MissingBankAccountException;
import com.fptufa26.ecomotion.finance.exception.PaymentRequestNotFoundException;
import com.fptufa26.ecomotion.finance.repository.PaymentEvidenceRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentEvidenceRepository paymentEvidenceRepository;
    private final FileStorageService fileStorageService;
    private final ProviderBankAccountRepository providerBankAccountRepository;
    private final BookingPaymentLedgerService bookingPaymentLedgerService;
    private final VehicleCatalogService vehicleCatalogService;
    private final ScheduleService scheduleService;
    private final UserService userService;
    private final Clock clock;
    private final Environment environment;

    @Override
    @Transactional
    public PaymentRequest createDepositPaymentRequest(Booking booking) {
        if (booking.getStatus() != BookingStatus.ACCEPTED
                && booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
            throw new InvalidPaymentOperationException("Chỉ có thể tạo yêu cầu đặt cọc cho đơn thuê đang chờ đặt cọc");
        }

        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByBooking_IdAndPaymentType(
                booking.getId(), PaymentRequestType.DEPOSIT);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        Provider provider = booking.getVehicle().getProvider();
        ProviderBankAccount bankAccount = resolveActiveBankAccount(provider);

        BigDecimal depositAmount = calculateDepositAmount(booking);

        String transferRef = generateTransferReference(booking.getBookingCode(), PaymentRequestType.DEPOSIT);

        PaymentRequest pr = new PaymentRequest();
        pr.setBooking(booking);
        pr.setPaymentType(PaymentRequestType.DEPOSIT);
        pr.setAmount(depositAmount);
        pr.setPaidAmount(BigDecimal.ZERO);
        pr.setCurrency("VND");
        pr.setTransferReference(transferRef);

        pr.setBankCodeSnapshot(bankAccount.getBankCode());
        pr.setBankNameSnapshot(bankAccount.getBankName());
        pr.setAccountNumberSnapshot(bankAccount.getAccountNumber());
        pr.setAccountHolderSnapshot(bankAccount.getAccountHolderName());
        pr.setReceivingBankAccount(bankAccount);

        pr.setStatus(PaymentRequestStatus.PENDING);
        Instant now = clock.instant();
        pr.setCreatedAt(now);
        pr.setUpdatedAt(now);

        Instant expiresAt = booking.getStatus() == BookingStatus.AWAITING_DEPOSIT
                ? now.plus(15, ChronoUnit.MINUTES)
                : now.plus(24, ChronoUnit.HOURS);
        if (booking.getStatus() == BookingStatus.ACCEPTED
                && booking.getPickupTime() != null
                && booking.getPickupTime().minus(2, ChronoUnit.HOURS).isBefore(expiresAt)) {
            expiresAt = booking.getPickupTime().minus(2, ChronoUnit.HOURS);
        }
        pr.setExpiresAt(expiresAt);

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Created DEPOSIT PaymentRequest #{} for Booking {} with transferRef={}",
                saved.getId(), booking.getBookingCode(), transferRef);
        return saved;
    }

    @Override
    @Transactional
    public PaymentRequestResponse reissuePaymentRequest(Long paymentRequestId, Long currentUserId) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));
        Booking booking = pr.getBooking();
        if (!booking.getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Bạn không có quyền cấp lại yêu cầu thanh toán này");
        }
        if (pr.getPaymentType() != PaymentRequestType.DEPOSIT
                || (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT
                    && booking.getStatus() != BookingStatus.ACCEPTED)) {
            throw new InvalidPaymentOperationException("Yêu cầu đặt cọc không thể được cấp lại ở trạng thái hiện tại");
        }
        boolean expiredByTime = pr.getExpiresAt() != null && !pr.getExpiresAt().isAfter(clock.instant());
        if (pr.getStatus() != PaymentRequestStatus.EXPIRED
                && pr.getStatus() != PaymentRequestStatus.REJECTED
                && !(pr.getStatus() == PaymentRequestStatus.PENDING && expiredByTime)) {
            throw new InvalidPaymentOperationException("Chỉ có thể cấp lại yêu cầu đặt cọc đã hết hạn hoặc bị từ chối");
        }

        Instant operationalCutoff = booking.getPickupTime().minus(60, ChronoUnit.MINUTES);
        Instant expiresAt = minInstant(clock.instant().plus(15, ChronoUnit.MINUTES), operationalCutoff);
        if (!expiresAt.isAfter(clock.instant())) {
            throw new InvalidPaymentOperationException("Đã quá hạn thanh toán cọc trước giờ nhận xe");
        }
        pr.setStatus(PaymentRequestStatus.PENDING);
        pr.setExpiresAt(expiresAt);
        pr.setRejectionReason(null);
        pr.setIssueNote(null);
        pr.setUpdatedAt(clock.instant());
        if (booking.getStatus() == BookingStatus.AWAITING_DEPOSIT) {
            vehicleCatalogService.requireApprovedForUpdate(booking.getVehicle().getId());
            scheduleService.reissueHold(booking, expiresAt);
        }
        return toResponse(paymentRequestRepository.save(pr));
    }

    @Override
    @Transactional
    public PaymentRequestResponse reissueDepositByBookingCode(String bookingCode, Long currentUserId) {
        String normalizedCode = normalizeBookingCode(bookingCode);
        PaymentRequest request = paymentRequestRepository.findByBookingCodeAndTypeForUpdate(
                        normalizedCode, PaymentRequestType.DEPOSIT)
                .orElseThrow(() -> new PaymentRequestNotFoundException(
                        "Không tìm thấy yêu cầu đặt cọc của đơn " + normalizedCode));
        return reissuePaymentRequest(request.getId(), currentUserId);
    }

    @Override
    @Transactional
    public PaymentRequest createFinalBalancePaymentRequest(Long bookingId) {
        Booking booking = bookingPaymentLedgerService.requireBooking(bookingId);

        // Check if FINAL_BALANCE request already exists
        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByBooking_IdAndPaymentType(
                booking.getId(), PaymentRequestType.FINAL_BALANCE);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        BigDecimal remaining = calculateRemainingAmount(booking);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentOperationException("Đơn thuê đã thanh toán đủ hoặc không còn số dư cần thanh toán quyết toán");
        }

        Provider provider = booking.getVehicle().getProvider();
        ProviderBankAccount bankAccount = resolveActiveBankAccount(provider);

        String transferRef = generateTransferReference(booking.getBookingCode(), PaymentRequestType.FINAL_BALANCE);

        PaymentRequest pr = new PaymentRequest();
        pr.setBooking(booking);
        pr.setPaymentType(PaymentRequestType.FINAL_BALANCE);
        pr.setAmount(remaining);
        pr.setPaidAmount(BigDecimal.ZERO);
        pr.setCurrency("VND");
        pr.setTransferReference(transferRef);

        pr.setBankCodeSnapshot(bankAccount.getBankCode());
        pr.setBankNameSnapshot(bankAccount.getBankName());
        pr.setAccountNumberSnapshot(bankAccount.getAccountNumber());
        pr.setAccountHolderSnapshot(bankAccount.getAccountHolderName());

        pr.setStatus(PaymentRequestStatus.PENDING);
        Instant now = clock.instant();
        pr.setCreatedAt(now);
        pr.setUpdatedAt(now);
        pr.setExpiresAt(now.plus(48, ChronoUnit.HOURS));

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Created FINAL_BALANCE PaymentRequest #{} for Booking {} with remaining={}",
                saved.getId(), booking.getBookingCode(), remaining);
        return saved;
    }

    @Override
    public List<PaymentRequestResponse> getPaymentRequestsByBooking(Long bookingId, Long currentUserId) {
        Booking booking = bookingPaymentLedgerService.requireBooking(bookingId);

        validateAccessToBooking(booking, currentUserId);

        List<PaymentRequest> requests = paymentRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        return requests.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PaymentRequestResponse> getPaymentRequestsByBookingCode(String bookingCode, Long currentUserId) {
        String normalizedCode = normalizeBookingCode(bookingCode);
        List<PaymentRequest> requests = paymentRequestRepository
                .findByBooking_BookingCodeOrderByCreatedAtDesc(normalizedCode);
        if (requests.isEmpty()) {
            throw new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán của đơn " + normalizedCode);
        }
        validateAccessToBooking(requests.getFirst().getBooking(), currentUserId);
        return requests.stream().map(this::toResponse).toList();
    }

    @Override
    public PaymentRequestResponse getPaymentRequestDetail(Long paymentRequestId, Long currentUserId) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        validateAccessToBooking(pr.getBooking(), currentUserId);

        return toResponse(pr);
    }

    @Override
    @Transactional
    public PaymentEvidenceResponse submitPaymentProof(Long paymentRequestId, Long currentUserId, SubmitProofRequest request) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        User user = userService.findById(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng không tồn tại"));

        // Only the renter of this booking can submit proof
        if (!pr.getBooking().getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Chỉ khách hàng thuê xe mới có quyền gửi bằng chứng thanh toán cho đơn này");
        }

        if (!Set.of(PaymentRequestStatus.PENDING, PaymentRequestStatus.REJECTED,
                PaymentRequestStatus.PARTIALLY_PAID, PaymentRequestStatus.EXPIRED).contains(pr.getStatus())) {
            throw new InvalidPaymentOperationException(
                    "Yêu cầu thanh toán hiện không nhận thêm bằng chứng chuyển khoản");
        }
        validatePayableStage(pr);

        PaymentEvidence evidence = new PaymentEvidence();
        evidence.setPaymentRequest(pr);
        evidence.setProofImageUrl(request.proofImageUrl());
        evidence.setTransferredAmount(request.transferredAmount());
        evidence.setTransactionReference(request.transactionReference());
        evidence.setTransferredAt(request.transferredAt() != null ? request.transferredAt() : clock.instant());
        evidence.setNote(request.note());
        evidence.setSubmittedBy(user);
        evidence.setStatus(PaymentEvidenceStatus.PENDING);
        evidence.setCreatedAt(clock.instant());

        PaymentEvidence savedEvidence = paymentEvidenceRepository.save(evidence);
        pr.getEvidences().add(savedEvidence);

        registerTimelyProof(pr);
        pr.setUpdatedAt(clock.instant());
        paymentRequestRepository.save(pr);

        log.info("Renter {} submitted proof for PaymentRequest #{}", currentUserId, paymentRequestId);
        return toEvidenceResponse(savedEvidence);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentEvidenceFileResponse getPaymentEvidenceFile(Long evidenceId, Long currentUserId) {
        PaymentEvidence evidence = paymentEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(
                        "Không tìm thấy bằng chứng thanh toán #" + evidenceId));
        validateAccessToBooking(evidence.getPaymentRequest().getBooking(), currentUserId);
        String storedPath = evidence.getProofImageUrl();
        if (!isManagedEvidencePath(storedPath)) {
            throw new PaymentRequestNotFoundException("Bằng chứng này không được lưu trong hệ thống");
        }
        String lower = storedPath.toLowerCase(Locale.ROOT);
        String contentType = lower.endsWith(".png") ? "image/png"
                : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        String filename = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        return new PaymentEvidenceFileResponse(fileStorageService.read(storedPath), contentType, filename);
    }

    @Override
    @Transactional
    public PaymentEvidenceResponse submitPaymentProof(
            Long paymentRequestId, Long currentUserId, MultipartFile file,
            BigDecimal transferredAmount, String transactionReference,
            Instant transferredAt, String note) {
        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException(
                        "Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));
        if (!pr.getBooking().getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException(
                    "Chỉ khách hàng thuê xe mới có quyền gửi bằng chứng thanh toán cho đơn này");
        }
        if (!Set.of(PaymentRequestStatus.PENDING, PaymentRequestStatus.REJECTED,
                PaymentRequestStatus.PARTIALLY_PAID, PaymentRequestStatus.EXPIRED).contains(pr.getStatus())) {
            throw new InvalidPaymentOperationException(
                    "Yêu cầu thanh toán hiện không nhận thêm bằng chứng chuyển khoản");
        }
        validatePayableStage(pr);
        if (file == null || file.isEmpty()) {
            throw new InvalidPaymentOperationException("Ảnh chứng từ không được để trống");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new InvalidPaymentOperationException("Ảnh chứng từ không được vượt quá 5 MB");
        }
        String contentType = file.getContentType();
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new InvalidPaymentOperationException("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        }
        BigDecimal qrAmount = pr.getAmount().subtract(
                pr.getPaidAmount() == null ? BigDecimal.ZERO : pr.getPaidAmount()).max(BigDecimal.ZERO);
        if (qrAmount.compareTo(BigDecimal.ZERO) <= 0) {
            qrAmount = pr.getAmount();
        }
        if (qrAmount.compareTo(new BigDecimal("1000")) < 0) {
            throw new InvalidPaymentOperationException("Số tiền trên yêu cầu thanh toán tối thiểu 1.000 ₫");
        }
        if (transactionReference != null && transactionReference.length() > 100) {
            throw new InvalidPaymentOperationException("Mã giao dịch ngân hàng tối đa 100 ký tự");
        }
        if (note != null && note.length() > 500) {
            throw new InvalidPaymentOperationException("Ghi chú tối đa 500 ký tự");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasValidImageSignature(bytes, contentType)) {
                throw new InvalidPaymentOperationException("Nội dung tệp không khớp định dạng ảnh JPG, PNG hoặc WebP");
            }
            String extension = switch (contentType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> throw new InvalidPaymentOperationException("Định dạng ảnh không hợp lệ");
            };
            String immutableName = "payment-proof-" + paymentRequestId + "-" + UUID.randomUUID() + extension;
            String storedPath = fileStorageService.store(
                    "payment-evidence", currentUserId, immutableName, bytes, contentType);
            registerRollbackCleanup(storedPath);
            return submitPaymentProof(paymentRequestId, currentUserId,
                    new SubmitProofRequest(storedPath, qrAmount,
                            transactionReference, transferredAt, note));
        } catch (java.io.IOException ex) {
            throw new InvalidPaymentOperationException("Không thể đọc ảnh chứng từ");
        }
    }

    private void validatePayableStage(PaymentRequest request) {
        BookingStatus bookingStatus = request.getBooking().getStatus();
        boolean valid = request.getPaymentType() == PaymentRequestType.DEPOSIT
                ? bookingStatus == BookingStatus.ACCEPTED
                || bookingStatus == BookingStatus.AWAITING_DEPOSIT
                || (bookingStatus == BookingStatus.CANCELLED
                && request.getBooking().getCancelReason() == com.fptufa26.ecomotion.booking.enums.CancelReason.DEPOSIT_TIMEOUT)
                : request.getPaymentType() == PaymentRequestType.FINAL_BALANCE
                && bookingStatus == BookingStatus.AWAITING_FINAL_PAYMENT;
        if (!valid) {
            throw new InvalidPaymentOperationException("Yêu cầu thanh toán không thuộc giai đoạn có thể gửi chứng từ");
        }
    }

    @Override
    @Transactional
    public boolean expireDepositWithoutProof(Long bookingId) {
        PaymentRequest request = paymentRequestRepository
                .findByBooking_IdAndPaymentType(bookingId, PaymentRequestType.DEPOSIT)
                .orElse(null);
        if (request == null || request.getFirstProofAt() != null || !request.getEvidences().isEmpty()
                || request.getStatus() == PaymentRequestStatus.CONFIRMED
                || request.getStatus() == PaymentRequestStatus.VOIDED) {
            return false;
        }
        request.setStatus(PaymentRequestStatus.EXPIRED);
        request.setIssueNote("Đã hết thời gian giữ lịch 15 phút và chưa nhận được chứng từ");
        request.setUpdatedAt(clock.instant());
        return true;
    }

    @Override
    public Optional<PaymentRequest> findPaymentRequest(Long bookingId, PaymentRequestType type) {
        return paymentRequestRepository.findByBooking_IdAndPaymentType(bookingId, type);
    }

    private void registerTimelyProof(PaymentRequest request) {
        Instant now = clock.instant();
        if (request.getFirstProofAt() == null) {
            request.setFirstProofAt(now);
            Instant operationalCutoff = request.getBooking().getPickupTime().minus(60, ChronoUnit.MINUTES);
            boolean submittedAfterDeadline = request.getExpiresAt() != null
                    && !request.getExpiresAt().isAfter(now);
            Instant reconciliationDeadline = minInstant(now.plus(60, ChronoUnit.MINUTES), operationalCutoff);
            if (submittedAfterDeadline || !reconciliationDeadline.isAfter(now)) {
                request.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                request.setIssueNote("Chứng từ được gửi sau thời hạn đối soát; cần xử lý tiền đến muộn thủ công");
                return;
            }
            request.setReconciliationDeadline(reconciliationDeadline);
            request.setExpiresAt(reconciliationDeadline);
            if (request.getPaymentType() == PaymentRequestType.DEPOSIT
                    && request.getBooking().getStatus() == BookingStatus.AWAITING_DEPOSIT) {
                scheduleService.renewHold(request.getBooking().getId(), reconciliationDeadline);
            }
        }
        if (request.getStatus() != PaymentRequestStatus.REVIEW_REQUIRED) {
            request.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        }
    }

    private Instant minInstant(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private boolean hasValidImageSignature(byte[] bytes, String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return bytes.length >= signature.length
                    && java.util.stream.IntStream.range(0, signature.length)
                    .allMatch(index -> bytes[index] == signature[index]);
        }
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private void registerRollbackCleanup(String storedPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        fileStorageService.delete(storedPath);
                    } catch (RuntimeException ex) {
                        log.error("Could not clean up rolled-back payment proof {}", storedPath, ex);
                    }
                }
            }
        });
    }

    @Override
    public List<ProviderTransactionResponse> getBookingPaymentHistory(Long bookingId, Long currentUserId) {
        Booking booking = bookingPaymentLedgerService.requireBooking(bookingId);

        validateAccessToBooking(booking, currentUserId);

        List<PaymentTransaction> transactions = bookingPaymentLedgerService.listTransactionsAscending(bookingId);
        return transactions.stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    @Override
    public PageResponse<PaymentRequestResponse> listProviderPaymentRequests(Long currentUserId, String status, int offset, int limit) {
        Provider provider = resolveProvider(currentUserId);

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int pageIndex = safeOffset / safeLimit;
        Pageable pageable = PageRequest.of(pageIndex, safeLimit, DEFAULT_SORT);

        PaymentRequestStatus filterStatus = parseStatus(status);

        Page<PaymentRequest> page;
        if (filterStatus != null) {
            page = paymentRequestRepository.findByBooking_Vehicle_Provider_IdAndStatus(provider.getId(), filterStatus, pageable);
        } else {
            page = paymentRequestRepository.findByBooking_Vehicle_Provider_Id(provider.getId(), pageable);
        }

        List<PaymentRequestResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        boolean hasMore = (long) (pageIndex + 1) * safeLimit < page.getTotalElements();
        return new PageResponse<>(items, page.getTotalElements(), safeOffset, safeLimit, hasMore);
    }

    @Override
    @Transactional
    public PaymentRequestResponse confirmPayment(Long paymentRequestId, Long currentUserId, ConfirmPaymentRequest request) {
        Provider provider = resolveProvider(currentUserId);

        PaymentRequest pr = paymentRequestRepository.findByIdForUpdate(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        if (!pr.getBooking().getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên yêu cầu thanh toán của nhà cung cấp khác");
        }

        if (pr.getStatus() != PaymentRequestStatus.PROOF_SUBMITTED) {
            throw new InvalidPaymentOperationException("Cần chứng từ chuyển khoản hợp lệ trước khi xác nhận thanh toán");
        }

        BigDecimal neededAmount = pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO);
        BigDecimal receivedAmount = (request.receivedAmount() != null && request.receivedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.receivedAmount()
                : neededAmount;

        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentOperationException("Số tiền xác nhận phải lớn hơn 0");
        }

        String bankRef = request.bankTransactionRef() != null && !request.bankTransactionRef().isBlank()
                ? request.bankTransactionRef().trim().toUpperCase(Locale.ROOT)
                : null;
        ProviderBankAccount receivingAccount = pr.getReceivingBankAccount() != null
                ? pr.getReceivingBankAccount()
                : resolveActiveBankAccount(provider);
        pr.setReceivingBankAccount(receivingAccount);
        if (bankRef == null && (request.note() == null || request.note().isBlank())) {
            throw new InvalidPaymentOperationException(
                    "Cần mã giao dịch ngân hàng hoặc ghi chú xác minh thủ công");
        }
        if (bankRef != null && bookingPaymentLedgerService
                .gatewayReferenceExists(receivingAccount.getId(), bankRef)) {
            throw new BankAccountConflictException("Giao dịch ngân hàng này đã được đối soát trước đó");
        }

        BigDecimal appliedAmount = receivedAmount.min(neededAmount);
        BigDecimal excessAmount = receivedAmount.subtract(appliedAmount).max(BigDecimal.ZERO);
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBooking(pr.getBooking());
        tx.setType(pr.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT);
        tx.setDirection(PaymentDirection.IN);
        tx.setMethod(PaymentMethod.BANK_TRANSFER);
        tx.setAmount(receivedAmount);
        tx.setAppliedAmount(appliedAmount);
        tx.setStatus(PaymentStatus.PAID);
        tx.setPaidAt(clock.instant());
        tx.setGatewayRef(bankRef);
        tx.setReceivingBankAccount(receivingAccount);
        tx.setProcessingNote(request.note());
        tx.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        tx.setConfirmedBy(provider.getUser());
        tx.setPaymentRequest(pr);

        tx.setBankCodeSnapshot(pr.getBankCodeSnapshot());
        tx.setBankNameSnapshot(pr.getBankNameSnapshot());
        tx.setBankAccountNoSnapshot(maskAccountNumber(pr.getAccountNumberSnapshot()));
        tx.setBankAccountHolderSnapshot(pr.getAccountHolderSnapshot());

        BigDecimal newPaidTotal = pr.getPaidAmount().add(appliedAmount).min(pr.getAmount());
        int comparison = newPaidTotal.compareTo(pr.getAmount());

        if (comparison >= 0) {
            Booking booking = pr.getBooking();
            if (pr.getPaymentType() == PaymentRequestType.DEPOSIT) {
                confirmDepositAndLockSchedule(booking, tx);
            } else if (pr.getPaymentType() == PaymentRequestType.FINAL_BALANCE) {
                if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
                    throw new InvalidPaymentOperationException("Đơn thuê chưa ở bước thanh toán quyết toán");
                }
                booking.setStatus(BookingStatus.COMPLETED);
                bookingPaymentLedgerService.saveBooking(booking);
            }
        }

        bookingPaymentLedgerService.saveTransaction(tx);

        if (excessAmount.compareTo(BigDecimal.ZERO) > 0) {
            createExcessRefund(pr, excessAmount, provider.getUser());
        }

        pr.setPaidAmount(newPaidTotal);
        pr.setConfirmedBy(provider.getUser());
        pr.setConfirmedAt(clock.instant());
        pr.setUpdatedAt(clock.instant());

        if (comparison >= 0) {
            pr.setStatus(PaymentRequestStatus.CONFIRMED);
        } else if (comparison < 0) {
            pr.setStatus(PaymentRequestStatus.PARTIALLY_PAID);
        }
        if (excessAmount.compareTo(BigDecimal.ZERO) > 0) {
            pr.setIssueNote("Đã ghi nhận tiền chuyển thừa và tạo yêu cầu hoàn " + excessAmount + " VND");
        }

        // Mark latest evidence as accepted if present
        if (!pr.getEvidences().isEmpty()) {
            PaymentEvidence latestEvidence = pr.getEvidences().get(0);
            latestEvidence.setStatus(PaymentEvidenceStatus.ACCEPTED);
            latestEvidence.setReviewedAt(clock.instant());
            latestEvidence.setReviewedBy(provider.getUser());
            paymentEvidenceRepository.save(latestEvidence);
        }

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Provider {} confirmed payment for PaymentRequest #{} with amount={}",
                provider.getId(), paymentRequestId, receivedAmount);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentRequestResponse rejectPayment(Long paymentRequestId, Long currentUserId, RejectPaymentRequest request) {
        Provider provider = resolveProvider(currentUserId);

        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        if (!pr.getBooking().getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên yêu cầu thanh toán của nhà cung cấp khác");
        }

        if (pr.getStatus() == PaymentRequestStatus.CONFIRMED) {
            throw new InvalidPaymentOperationException("Không thể từ chối yêu cầu thanh toán đã được xác nhận thành công");
        }

        pr.setStatus(PaymentRequestStatus.REJECTED);
        pr.setRejectionReason(request.reason());
        pr.setUpdatedAt(clock.instant());

        if (!pr.getEvidences().isEmpty()) {
            PaymentEvidence latest = pr.getEvidences().get(0);
            latest.setStatus(PaymentEvidenceStatus.REJECTED);
            latest.setReviewedAt(clock.instant());
            latest.setReviewedBy(provider.getUser());
            paymentEvidenceRepository.save(latest);
        }

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Provider {} rejected PaymentRequest #{} with reason: {}",
                provider.getId(), paymentRequestId, request.reason());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public PaymentRequestResponse reportIssue(Long paymentRequestId, Long currentUserId, ReportIssueRequest request) {
        Provider provider = resolveProvider(currentUserId);

        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        if (!pr.getBooking().getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên yêu cầu thanh toán của nhà cung cấp khác");
        }

        pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
        pr.setIssueNote(request.issueNote());
        pr.setUpdatedAt(clock.instant());

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Provider {} reported issue on PaymentRequest #{}: {}",
                provider.getId(), paymentRequestId, request.issueNote());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SimulatePaymentResponse simulatePayment(Long paymentRequestId, Long adminUserId, SimulatePaymentRequest request) {
        // Enforce Sandbox restrictions
        validateSandboxEnvironment();

        User admin = userService.findById(adminUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng quản trị không tồn tại"));

        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        String scenario = request.scenario() != null ? request.scenario().trim().toUpperCase(Locale.ROOT) : "SUCCESS";
        BigDecimal amount = (request.simulatedAmount() != null && request.simulatedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.simulatedAmount()
                : pr.getAmount();

        String ref = (request.bankTransactionRef() != null && !request.bankTransactionRef().isBlank())
                ? request.bankTransactionRef().trim()
                : "MOCK-TX-" + System.currentTimeMillis();

        switch (scenario) {
            case "SUCCESS": {
                if (pr.getStatus() == PaymentRequestStatus.CONFIRMED) {
                    return new SimulatePaymentResponse(false, scenario, "Yêu cầu thanh toán đã được xác nhận trước đó", pr.getId(), null, pr.getStatus());
                }

                PaymentTransaction tx = new PaymentTransaction();
                tx.setBooking(pr.getBooking());
                tx.setType(pr.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT);
                tx.setDirection(PaymentDirection.IN);
                tx.setMethod(PaymentMethod.BANK_TRANSFER);
                tx.setAmount(amount);
                tx.setStatus(PaymentStatus.PAID);
                tx.setPaidAt(clock.instant());
                tx.setGatewayRef(ref);
                tx.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED);
                tx.setConfirmedBy(admin);
                tx.setPaymentRequest(pr);
                tx.setBankCodeSnapshot(pr.getBankCodeSnapshot());
                tx.setBankNameSnapshot(pr.getBankNameSnapshot());
                tx.setBankAccountNoSnapshot(maskAccountNumber(pr.getAccountNumberSnapshot()));
                tx.setBankAccountHolderSnapshot(pr.getAccountHolderSnapshot());
                PaymentTransaction savedTx = bookingPaymentLedgerService.saveTransaction(tx);

                pr.setPaidAmount(amount);
                pr.setStatus(PaymentRequestStatus.CONFIRMED);
                pr.setConfirmedAt(clock.instant());
                pr.setConfirmedBy(admin);
                pr.setUpdatedAt(clock.instant());

                Booking booking = pr.getBooking();
                if (pr.getPaymentType() == PaymentRequestType.DEPOSIT && booking.getStatus() == BookingStatus.ACCEPTED) {
                    booking.setStatus(BookingStatus.CONFIRMED);
                    bookingPaymentLedgerService.saveBooking(booking);
                }
                paymentRequestRepository.save(pr);

                return new SimulatePaymentResponse(true, scenario, "Mô phỏng thanh toán thành công", pr.getId(), savedTx.getId(), pr.getStatus());
            }

            case "UNDERPAID": {
                BigDecimal half = amount.divide(new BigDecimal("2"), 0, RoundingMode.HALF_UP);
                PaymentTransaction tx = new PaymentTransaction();
                tx.setBooking(pr.getBooking());
                tx.setType(pr.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT);
                tx.setDirection(PaymentDirection.IN);
                tx.setMethod(PaymentMethod.BANK_TRANSFER);
                tx.setAmount(half);
                tx.setStatus(PaymentStatus.PAID);
                tx.setPaidAt(clock.instant());
                tx.setGatewayRef(ref);
                tx.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED);
                tx.setConfirmedBy(admin);
                tx.setPaymentRequest(pr);
                PaymentTransaction savedTx = bookingPaymentLedgerService.saveTransaction(tx);

                pr.setPaidAmount(half);
                pr.setStatus(PaymentRequestStatus.PARTIALLY_PAID);
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);

                return new SimulatePaymentResponse(true, scenario, "Mô phỏng chuyển thiếu tiền (chuyển 50%)", pr.getId(), savedTx.getId(), pr.getStatus());
            }

            case "OVERPAID": {
                BigDecimal extra = amount.add(new BigDecimal("100000.00"));
                PaymentTransaction tx = new PaymentTransaction();
                tx.setBooking(pr.getBooking());
                tx.setType(pr.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT);
                tx.setDirection(PaymentDirection.IN);
                tx.setMethod(PaymentMethod.BANK_TRANSFER);
                tx.setAmount(extra);
                tx.setStatus(PaymentStatus.PAID);
                tx.setPaidAt(clock.instant());
                tx.setGatewayRef(ref);
                tx.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED);
                tx.setConfirmedBy(admin);
                tx.setPaymentRequest(pr);
                PaymentTransaction savedTx = bookingPaymentLedgerService.saveTransaction(tx);

                pr.setPaidAmount(extra);
                pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                pr.setIssueNote("Mô phỏng khách hàng chuyển thừa +100.000 VND");
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);

                return new SimulatePaymentResponse(true, scenario, "Mô phỏng chuyển thừa tiền (chuyển dư 100.000 ₫)", pr.getId(), savedTx.getId(), pr.getStatus());
            }

            case "NO_TRANSFER": {
                pr.setStatus(PaymentRequestStatus.EXPIRED);
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);
                return new SimulatePaymentResponse(true, scenario, "Mô phỏng khách hàng không chuyển và quá hạn thanh toán", pr.getId(), null, pr.getStatus());
            }

            case "DUPLICATE": {
                if (bookingPaymentLedgerService.gatewayReferenceExists(ref)) {
                    throw new BankAccountConflictException("Mã giao dịch mô phỏng đã bị trùng lặp: " + ref);
                }
                // Save one transaction then throw conflict
                PaymentTransaction tx = new PaymentTransaction();
                tx.setBooking(pr.getBooking());
                tx.setType(PaymentType.DEPOSIT_PAY);
                tx.setDirection(PaymentDirection.IN);
                tx.setMethod(PaymentMethod.BANK_TRANSFER);
                tx.setAmount(amount);
                tx.setStatus(PaymentStatus.PAID);
                tx.setPaidAt(clock.instant());
                tx.setGatewayRef(ref);
                tx.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED);
                bookingPaymentLedgerService.saveTransaction(tx);

                throw new BankAccountConflictException("Mô phỏng phát hiện mã tham chiếu trùng lặp: " + ref);
            }

            case "WRONG_REFERENCE": {
                pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                pr.setIssueNote("Mô phỏng sai nội dung chuyển khoản: " + ref);
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);
                return new SimulatePaymentResponse(true, scenario, "Mô phỏng khách hàng chuyển sai nội dung đối chiếu", pr.getId(), null, pr.getStatus());
            }

            case "PROVIDER_REJECTED": {
                pr.setStatus(PaymentRequestStatus.REJECTED);
                pr.setRejectionReason("Mô phỏng nhà cung cấp từ chối bằng chứng do không thấy tiền vào tài khoản");
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);
                return new SimulatePaymentResponse(true, scenario, "Mô phỏng nhà cung cấp từ chối bằng chứng", pr.getId(), null, pr.getStatus());
            }

            case "DISPUTED": {
                pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                pr.setIssueNote("Mô phỏng khiếu nại phát sinh từ khách hàng hoặc nhà cung cấp");
                pr.setUpdatedAt(clock.instant());
                paymentRequestRepository.save(pr);
                return new SimulatePaymentResponse(true, scenario, "Mô phỏng giao dịch tranh chấp cần đối soát", pr.getId(), null, pr.getStatus());
            }

            default:
                throw new InvalidPaymentOperationException("Kịch bản mô phỏng không được hỗ trợ: " + scenario);
        }
    }

    @Override
    @Transactional
    public void seedSandboxData(Long adminUserId) {
        validateSandboxEnvironment();

        List<Provider> providers = userService.listProviders(null);
        for (Provider provider : providers) {
            if (provider.getBankAccountNo() == null || provider.getBankAccountNo().isBlank()) {
                provider.setBankCode("VCB");
                provider.setBankName("Vietcombank");
                provider.setBankAccountNo("0123456789");
                provider.setBankAccountHolder(provider.getDisplayName() != null ? provider.getDisplayName().toUpperCase(Locale.ROOT) : "ECO PROVIDER");
            }

            if (!providerBankAccountRepository.existsByProvider_IdAndStatus(provider.getId(), BankAccountStatus.ACTIVE)) {
                ProviderBankAccount pba = new ProviderBankAccount();
                pba.setProvider(provider);
                pba.setBankCode(provider.getBankCode());
                pba.setBankName(provider.getBankName());
                pba.setAccountNumber(provider.getBankAccountNo());
                pba.setAccountHolderName(provider.getBankAccountHolder());
                pba.setStatus(BankAccountStatus.ACTIVE);
                pba.setCreatedAt(clock.instant());
                pba.setUpdatedAt(clock.instant());
                providerBankAccountRepository.save(pba);
            }
        }
        log.info("Admin {} seeded sandbox bank accounts for all providers", adminUserId);
    }

    private void validateSandboxEnvironment() {
        String[] profiles = environment.getActiveProfiles();
        boolean isProd = Arrays.stream(profiles).anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (isProd) {
            throw new InvalidPaymentOperationException("Tính năng Sandbox bị vô hiệu hóa trên môi trường Production vì lý do an toàn tài chính");
        }
    }

    private Provider resolveProvider(Long currentUserId) {
        return userService.findProviderByUserId(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng hiện tại không có hồ sơ nhà cung cấp"));
    }

    private ProviderBankAccount resolveActiveBankAccount(Provider provider) {
        Optional<ProviderBankAccount> pbaOpt = providerBankAccountRepository.findByProvider_IdAndStatus(
                provider.getId(), BankAccountStatus.ACTIVE);
        if (pbaOpt.isPresent()) {
            return pbaOpt.get();
        }

        if (provider.getBankAccountNo() != null && !provider.getBankAccountNo().isBlank()
                && provider.getBankAccountHolder() != null && !provider.getBankAccountHolder().isBlank()) {
            String bankCode = provider.getBankCode() != null ? provider.getBankCode() : "VCB";
            String bankName = provider.getBankName() != null ? provider.getBankName() : "Vietcombank";

            ProviderBankAccount fallback = new ProviderBankAccount();
            fallback.setProvider(provider);
            fallback.setBankCode(bankCode);
            fallback.setBankName(bankName);
            fallback.setAccountNumber(provider.getBankAccountNo());
            fallback.setAccountHolderName(provider.getBankAccountHolder());
            fallback.setStatus(BankAccountStatus.ACTIVE);
            fallback.setCreatedAt(clock.instant());
            fallback.setUpdatedAt(clock.instant());
            return providerBankAccountRepository.save(fallback);
        }

        throw new MissingBankAccountException("Nhà cung cấp chưa cấu hình tài khoản ngân hàng nhận tiền. Vui lòng thiết lập tài khoản ngân hàng trước khi phát hành yêu cầu thanh toán.");
    }

    private void confirmDepositAndLockSchedule(Booking booking, PaymentTransaction transaction) {
        if (booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED
                || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
            return;
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED
                && booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
            throw new InvalidPaymentOperationException("Đơn thuê không còn ở trạng thái chờ xác nhận cọc");
        }

        if (booking.getStatus() == BookingStatus.AWAITING_DEPOSIT) {
            vehicleCatalogService.requireApprovedForUpdate(booking.getVehicle().getId());
            scheduleService.confirmHold(booking.getId());
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingPaymentLedgerService.saveBooking(booking);
            return;
        }

        Vehicle lockedVehicle = vehicleCatalogService.requireApprovedForUpdate(booking.getVehicle().getId());
        BookingPolicySnapshot snapshot = bookingPaymentLedgerService.findSnapshot(booking.getId())
                .orElse(null);
        int slotLengthMinutes = snapshot != null
                ? snapshot.getSlotLengthMinutes()
                : booking.getPlan().getSlotLengthMinutes();
        int slots = bookingPaymentLedgerService.findCharge(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        Instant end = booking.getPickupTime().plus(
                Duration.ofMinutes((long) slotLengthMinutes * slots));
        AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(
                lockedVehicle, booking.getPickupTime(), end);

        if (!scheduleService.isRangeFree(lockedVehicle.getId(), window.start(), window.end())) {
            throw new ConflictException("Xe vừa được khóa bởi lịch khác; chưa thể xác nhận khoản cọc");
        }

        BookingCharge deposit = bookingPaymentLedgerService
                .findCharge(booking.getId(), ChargeType.DEPOSIT)
                .orElseGet(() -> {
                    BookingCharge charge = new BookingCharge();
                    charge.setBooking(booking);
                    charge.setChargeType(ChargeType.DEPOSIT);
                    charge.setDescription("Cọc giữ chỗ qua VietQR");
                    charge.setUnitPrice(booking.getTotalAmount());
                    charge.setAppliedRate(snapshot != null
                            ? snapshot.getDepositRatio()
                            : booking.getVehicle().getProvider().getDepositRatio());
                    charge.setAmount(transaction.getPaymentRequest().getAmount());
                    charge.setCreatedBy(booking.getRenter().getUser());
                    charge.setWaived(false);
                    return bookingPaymentLedgerService.saveCharge(charge);
                });

        transaction.setCharge(deposit);
        scheduleService.book(lockedVehicle, booking, booking.getPickupTime(), end);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingPaymentLedgerService.saveBooking(booking);
        log.info("Booking {} transitioned to CONFIRMED and its schedule was locked after DEPOSIT confirmation",
                booking.getBookingCode());
    }

    private void createExcessRefund(PaymentRequest request, BigDecimal excessAmount, User processor) {
        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(request.getBooking());
        refund.setType(PaymentType.REFUND);
        refund.setDirection(PaymentDirection.OUT);
        refund.setMethod(PaymentMethod.BANK_TRANSFER);
        refund.setAmount(excessAmount);
        refund.setAppliedAmount(BigDecimal.ZERO);
        refund.setStatus(PaymentStatus.PENDING);
        refund.setPaymentRequest(request);
        refund.setConfirmedBy(processor);
        refund.setProcessingNote("Hoàn tiền chuyển thừa cho yêu cầu thanh toán #" + request.getId());
        bookingPaymentLedgerService.saveTransaction(refund);
    }

    private BigDecimal calculateDepositAmount(Booking booking) {
        BookingPolicySnapshot snapshot = bookingPaymentLedgerService.findSnapshot(booking.getId()).orElse(null);
        if (snapshot != null && snapshot.getDepositAmount() != null) {
            return snapshot.getDepositAmount().setScale(2, RoundingMode.HALF_UP);
        }
        PlanType planType = snapshot != null ? snapshot.getPlanType() : booking.getPlan().getPlanType();
        BigDecimal upfrontAmount = snapshot != null
                ? snapshot.getUpfrontAmount()
                : booking.getPlan().getUpfrontAmount();
        if (planType == PlanType.YEARLY && upfrontAmount != null
                && upfrontAmount.compareTo(BigDecimal.ZERO) > 0) {
            return upfrontAmount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = snapshot != null && snapshot.getDepositRatio() != null
                ? snapshot.getDepositRatio()
                : (booking.getVehicle().getProvider().getDepositRatio() != null
                    ? booking.getVehicle().getProvider().getDepositRatio()
                    : new BigDecimal("0.3000"));

        BigDecimal base = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal minimum = snapshot != null && snapshot.getMinimumDepositAmount() != null
                ? snapshot.getMinimumDepositAmount()
                : BigDecimal.ZERO;
        BigDecimal calculated = base.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        return calculated.max(minimum).min(base).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateRemainingAmount(Long bookingId) {
        Booking booking = bookingPaymentLedgerService.requireBooking(bookingId);
        return calculateRemainingAmount(booking);
    }

    private BigDecimal calculateRemainingAmount(Booking booking) {
        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingPaymentLedgerService.listCharges(booking.getId());
        BigDecimal approvedCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPayable = baseRental.add(approvedCharges).max(BigDecimal.ZERO);

        List<PaymentTransaction> transactions = bookingPaymentLedgerService.listTransactions(booking.getId());
        BigDecimal allocatedPayments = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(t -> t.getAppliedAmount() != null && t.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0
                        ? t.getAppliedAmount()
                        : t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalPayable.subtract(allocatedPayments).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateTransferReference(String bookingCode, PaymentRequestType type) {
        String cleanCode = bookingCode == null ? "" : bookingCode.replace("-", "").toUpperCase(Locale.ROOT);
        while (cleanCode.startsWith("EMEM")) {
            cleanCode = cleanCode.substring(2);
        }
        if (!cleanCode.startsWith("EM")) {
            cleanCode = "EM" + cleanCode;
        }
        String suffix = (type == PaymentRequestType.DEPOSIT) ? "DEP" : "FIN";
        return cleanCode + suffix;
    }

    private void validateAccessToBooking(Booking booking, Long currentUserId) {
        User user = userService.findById(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng không tồn tại"));

        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (booking.getRenter().getUser().getId().equals(currentUserId)) {
            return;
        }

        if (booking.getVehicle().getProvider().getUser().getId().equals(currentUserId)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền truy cập thông tin thanh toán của đơn thuê này");
    }

    private PaymentRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return PaymentRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeBookingCode(String bookingCode) {
        if (bookingCode == null || bookingCode.isBlank()) {
            throw new PaymentRequestNotFoundException("Mã đơn thuê không hợp lệ");
        }
        return bookingCode.trim().toUpperCase(Locale.ROOT);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    private String buildVietQrUrl(PaymentRequest pr) {
        try {
            String cleanAccount = pr.getAccountNumberSnapshot().trim().replace(" ", "");
            String cleanAmount = pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.HALF_UP).toPlainString();
            String encodedRef = URLEncoder.encode(pr.getTransferReference().trim(), StandardCharsets.UTF_8);
            String encodedHolder = URLEncoder.encode(pr.getAccountHolderSnapshot().trim().toUpperCase(Locale.ROOT), StandardCharsets.UTF_8);

            return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                    pr.getBankCodeSnapshot().trim(), cleanAccount, cleanAmount, encodedRef, encodedHolder);
        } catch (Exception ex) {
            return null;
        }
    }

    private PaymentRequestResponse toResponse(PaymentRequest pr) {
        BigDecimal remaining = pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO);
        String maskedAccount = maskAccountNumber(pr.getAccountNumberSnapshot());
        String qrUrl = buildVietQrUrl(pr);

        List<PaymentEvidenceResponse> evidenceResponses = pr.getEvidences().stream()
                .map(this::toEvidenceResponse)
                .toList();

        return new PaymentRequestResponse(
                pr.getId(),
                pr.getBooking().getId(),
                pr.getBooking().getBookingCode(),
                pr.getPaymentType(),
                pr.getAmount(),
                pr.getPaidAmount(),
                remaining,
                pr.getCurrency(),
                pr.getTransferReference(),
                pr.getBankCodeSnapshot(),
                pr.getBankNameSnapshot(),
                pr.getAccountNumberSnapshot(),
                maskedAccount,
                pr.getAccountHolderSnapshot(),
                pr.getStatus(),
                qrUrl,
                pr.getExpiresAt(),
                pr.getFirstProofAt(),
                pr.getReconciliationDeadline(),
                pr.getCreatedAt(),
                pr.getConfirmedAt(),
                pr.getRejectionReason(),
                pr.getIssueNote(),
                evidenceResponses
        );
    }

    private PaymentEvidenceResponse toEvidenceResponse(PaymentEvidence e) {
        String submitterName = e.getSubmittedBy() != null ? e.getSubmittedBy().getFullName() : "";
        return new PaymentEvidenceResponse(
                e.getId(),
                e.getPaymentRequest().getId(),
                evidenceViewUrl(e.getId(), e.getProofImageUrl()),
                e.getTransferredAmount(),
                e.getTransactionReference(),
                e.getTransferredAt(),
                e.getNote(),
                e.getSubmittedBy() != null ? e.getSubmittedBy().getId() : null,
                submitterName,
                e.getStatus(),
                e.getCreatedAt(),
                e.getReviewedAt()
        );
    }

    private String evidenceViewUrl(Long evidenceId, String storedPath) {
        if (isManagedEvidencePath(storedPath)) {
            return "/api/v1/payment-evidences/" + evidenceId + "/file";
        }
        // Legacy external URLs are intentionally not reflected to clients.
        return null;
    }

    private boolean isManagedEvidencePath(String storedPath) {
        if (storedPath == null) {
            return false;
        }
        return storedPath.matches("payment-evidence/[0-9]+/payment-proof-[a-zA-Z0-9._-]+")
                || storedPath.matches("booking/[0-9]+/payment-proof-[a-zA-Z0-9._-]+");
    }

    private ProviderTransactionResponse toTransactionResponse(PaymentTransaction tx) {
        String bookingCode = tx.getBooking() != null ? tx.getBooking().getBookingCode() : "";
        Long bookingId = tx.getBooking() != null ? tx.getBooking().getId() : null;

        return new ProviderTransactionResponse(
                tx.getId(),
                bookingId,
                bookingCode,
                tx.getType(),
                tx.getAmount(),
                tx.getDirection(),
                tx.getMethod(),
                tx.getStatus(),
                tx.getGatewayRef(),
                tx.getVerificationMethod() != null ? tx.getVerificationMethod() : PaymentVerificationMethod.PROVIDER_CONFIRMED,
                tx.getBankCodeSnapshot(),
                tx.getBankNameSnapshot(),
                tx.getBankAccountNoSnapshot(),
                tx.getBankAccountHolderSnapshot(),
                tx.getReceiptUrl() == null ? null : "/api/v1/payment-transactions/" + tx.getId() + "/refund-proof",
                tx.getProcessingNote(),
                tx.getPaidAt(),
                tx.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentRequest> findByBookingIdAndPaymentType(Long bookingId, PaymentRequestType paymentType) {
        return paymentRequestRepository.findByBooking_IdAndPaymentType(bookingId, paymentType);
    }

    @Override
    @Transactional
    public PaymentRequest savePaymentRequest(PaymentRequest paymentRequest) {
        return paymentRequestRepository.save(paymentRequest);
    }
}
