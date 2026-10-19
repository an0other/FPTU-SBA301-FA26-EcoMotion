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
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.RejectPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportIssueRequest;
import com.fptufa26.ecomotion.finance.dto.request.SimulatePaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.SubmitProofRequest;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
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
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentEvidenceRepository paymentEvidenceRepository;
    private final ProviderBankAccountRepository providerBankAccountRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Environment environment;

    @Override
    @Transactional
    public PaymentRequest createDepositPaymentRequest(Booking booking) {
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            throw new InvalidPaymentOperationException("Chỉ có thể tạo yêu cầu đặt cọc cho đơn thuê ở trạng thái ĐÃ CHẤP NHẬN (ACCEPTED)");
        }

        // Check if an existing DEPOSIT request is active
        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByBooking_IdAndPaymentType(
                booking.getId(), PaymentRequestType.DEPOSIT);
        if (existingOpt.isPresent()) {
            PaymentRequest existing = existingOpt.get();
            if (existing.getStatus() != PaymentRequestStatus.REJECTED && existing.getStatus() != PaymentRequestStatus.EXPIRED) {
                return existing;
            }
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

        pr.setStatus(PaymentRequestStatus.PENDING);
        Instant now = clock.instant();
        pr.setCreatedAt(now);
        pr.setUpdatedAt(now);

        // Expiry: 24h from now or pickupTime minus 2 hours, whichever is sooner
        Instant expiresAt = now.plus(24, ChronoUnit.HOURS);
        if (booking.getPickupTime() != null && booking.getPickupTime().minus(2, ChronoUnit.HOURS).isBefore(expiresAt)) {
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
    public PaymentRequest createFinalBalancePaymentRequest(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy đơn thuê #" + bookingId));

        // Check if FINAL_BALANCE request already exists
        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByBooking_IdAndPaymentType(
                booking.getId(), PaymentRequestType.FINAL_BALANCE);
        if (existingOpt.isPresent()) {
            PaymentRequest existing = existingOpt.get();
            if (existing.getStatus() != PaymentRequestStatus.REJECTED && existing.getStatus() != PaymentRequestStatus.EXPIRED) {
                return existing;
            }
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy đơn thuê #" + bookingId));

        validateAccessToBooking(booking, currentUserId);

        List<PaymentRequest> requests = paymentRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        return requests.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaymentRequestResponse getPaymentRequestDetail(Long paymentRequestId, Long currentUserId) {
        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        validateAccessToBooking(pr.getBooking(), currentUserId);

        return toResponse(pr);
    }

    @Override
    @Transactional
    public PaymentEvidenceResponse submitPaymentProof(Long paymentRequestId, Long currentUserId, SubmitProofRequest request) {
        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng không tồn tại"));

        // Only the renter of this booking can submit proof
        if (!pr.getBooking().getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Chỉ khách hàng thuê xe mới có quyền gửi bằng chứng thanh toán cho đơn này");
        }

        if (pr.getStatus() == PaymentRequestStatus.CONFIRMED) {
            throw new InvalidPaymentOperationException("Yêu cầu thanh toán này đã được xác nhận hoàn tất trước đó");
        }

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

        // Update PaymentRequest status to PROOF_SUBMITTED (customer transfer does NOT mean confirmed)
        if (pr.getStatus() == PaymentRequestStatus.PENDING || pr.getStatus() == PaymentRequestStatus.REJECTED) {
            pr.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        }
        pr.setUpdatedAt(clock.instant());
        paymentRequestRepository.save(pr);

        log.info("Renter {} submitted proof for PaymentRequest #{}", currentUserId, paymentRequestId);
        return toEvidenceResponse(savedEvidence);
    }

    @Override
    public List<ProviderTransactionResponse> getBookingPaymentHistory(Long bookingId, Long currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy đơn thuê #" + bookingId));

        validateAccessToBooking(booking, currentUserId);

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_IdOrderByCreatedAtAsc(bookingId);
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

        PaymentRequest pr = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentRequestNotFoundException("Không tìm thấy yêu cầu thanh toán #" + paymentRequestId));

        if (!pr.getBooking().getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thao tác trên yêu cầu thanh toán của nhà cung cấp khác");
        }

        if (pr.getStatus() == PaymentRequestStatus.CONFIRMED) {
            throw new InvalidPaymentOperationException("Yêu cầu thanh toán này đã được xác nhận trước đó");
        }

        BigDecimal neededAmount = pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO);
        BigDecimal receivedAmount = (request.receivedAmount() != null && request.receivedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.receivedAmount()
                : neededAmount;

        if (receivedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentOperationException("Số tiền xác nhận phải lớn hơn 0");
        }

        // Deduplication: Verify unique bank reference
        String bankRef = (request.bankTransactionRef() != null && !request.bankTransactionRef().isBlank())
                ? request.bankTransactionRef().trim()
                : pr.getTransferReference() + "-" + System.currentTimeMillis();

        if (paymentTransactionRepository.existsByGatewayRef(bankRef)) {
            throw new BankAccountConflictException("Mã tham chiếu ngân hàng '" + bankRef + "' đã tồn tại trên hệ thống");
        }

        // Record incoming PaymentTransaction
        PaymentTransaction tx = new PaymentTransaction();
        tx.setBooking(pr.getBooking());
        tx.setType(pr.getPaymentType() == PaymentRequestType.DEPOSIT ? PaymentType.DEPOSIT_PAY : PaymentType.SETTLEMENT);
        tx.setDirection(PaymentDirection.IN);
        tx.setMethod(PaymentMethod.BANK_TRANSFER);
        tx.setAmount(receivedAmount);
        tx.setStatus(PaymentStatus.PAID);
        tx.setPaidAt(clock.instant());
        tx.setGatewayRef(bankRef);
        tx.setVerificationMethod(PaymentVerificationMethod.PROVIDER_CONFIRMED);
        tx.setConfirmedBy(provider.getUser());
        tx.setPaymentRequest(pr);

        tx.setBankCodeSnapshot(pr.getBankCodeSnapshot());
        tx.setBankNameSnapshot(pr.getBankNameSnapshot());
        tx.setBankAccountNoSnapshot(maskAccountNumber(pr.getAccountNumberSnapshot()));
        tx.setBankAccountHolderSnapshot(pr.getAccountHolderSnapshot());

        paymentTransactionRepository.save(tx);

        // Update PaymentRequest paid amount and status
        BigDecimal newPaidTotal = pr.getPaidAmount().add(receivedAmount);
        pr.setPaidAmount(newPaidTotal);
        pr.setConfirmedBy(provider.getUser());
        pr.setConfirmedAt(clock.instant());
        pr.setUpdatedAt(clock.instant());

        if (newPaidTotal.compareTo(pr.getAmount()) >= 0) {
            pr.setStatus(PaymentRequestStatus.CONFIRMED);

            // If DEPOSIT is confirmed and Booking is ACCEPTED, transition Booking to CONFIRMED
            Booking booking = pr.getBooking();
            if (pr.getPaymentType() == PaymentRequestType.DEPOSIT && booking.getStatus() == BookingStatus.ACCEPTED) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                log.info("Booking {} transitioned to CONFIRMED after DEPOSIT payment confirmation", booking.getBookingCode());
            }
        } else {
            pr.setStatus(PaymentRequestStatus.PARTIALLY_PAID);
        }

        // Overpayment check
        if (newPaidTotal.compareTo(pr.getAmount()) > 0) {
            pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
            pr.setIssueNote("Khách hàng đã chuyển thừa: " + newPaidTotal.subtract(pr.getAmount()) + " VND");
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

        User admin = userRepository.findById(adminUserId)
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
                PaymentTransaction savedTx = paymentTransactionRepository.save(tx);

                pr.setPaidAmount(amount);
                pr.setStatus(PaymentRequestStatus.CONFIRMED);
                pr.setConfirmedAt(clock.instant());
                pr.setConfirmedBy(admin);
                pr.setUpdatedAt(clock.instant());

                Booking booking = pr.getBooking();
                if (pr.getPaymentType() == PaymentRequestType.DEPOSIT && booking.getStatus() == BookingStatus.ACCEPTED) {
                    booking.setStatus(BookingStatus.CONFIRMED);
                    bookingRepository.save(booking);
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
                PaymentTransaction savedTx = paymentTransactionRepository.save(tx);

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
                PaymentTransaction savedTx = paymentTransactionRepository.save(tx);

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
                if (paymentTransactionRepository.existsByGatewayRef(ref)) {
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
                paymentTransactionRepository.save(tx);

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

        List<Provider> providers = providerRepository.findAll();
        for (Provider provider : providers) {
            if (provider.getBankAccountNo() == null || provider.getBankAccountNo().isBlank()) {
                provider.setBankCode("VCB");
                provider.setBankName("Vietcombank");
                provider.setBankAccountNo("0123456789");
                provider.setBankAccountHolder(provider.getDisplayName() != null ? provider.getDisplayName().toUpperCase(Locale.ROOT) : "ECO PROVIDER");
                providerRepository.save(provider);
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
        return providerRepository.findByUser_Id(currentUserId)
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

    private BigDecimal calculateDepositAmount(Booking booking) {
        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (snapshot != null && snapshot.getUpfrontAmount() != null && snapshot.getUpfrontAmount().compareTo(BigDecimal.ZERO) > 0) {
            return snapshot.getUpfrontAmount().setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = snapshot != null && snapshot.getDepositRatio() != null
                ? snapshot.getDepositRatio()
                : (booking.getVehicle().getProvider().getDepositRatio() != null
                    ? booking.getVehicle().getProvider().getDepositRatio()
                    : new BigDecimal("0.3000"));

        BigDecimal base = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal calculated = base.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
        return calculated.max(new BigDecimal("1000.00")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRemainingAmount(Booking booking) {
        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        BigDecimal approvedCharges = charges.stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.DEPOSIT)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal approvedReductions = charges.stream()
                .filter(BookingCharge::isWaived)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPayable = baseRental.add(approvedCharges).subtract(approvedReductions).max(BigDecimal.ZERO);

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_Id(booking.getId());
        BigDecimal confirmedIncoming = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completedRefunds = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT && t.getType() == PaymentType.REFUND)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netConfirmed = confirmedIncoming.subtract(completedRefunds).max(BigDecimal.ZERO);
        return totalPayable.subtract(netConfirmed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateTransferReference(String bookingCode, PaymentRequestType type) {
        String cleanCode = bookingCode.replace("-", "").toUpperCase(Locale.ROOT);
        String suffix = (type == PaymentRequestType.DEPOSIT) ? "DEP" : "FIN";
        return "EM" + cleanCode + suffix;
    }

    private void validateAccessToBooking(Booking booking, Long currentUserId) {
        User user = userRepository.findById(currentUserId)
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
                e.getProofImageUrl(),
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
                tx.getPaidAt(),
                tx.getCreatedAt()
        );
    }
}
