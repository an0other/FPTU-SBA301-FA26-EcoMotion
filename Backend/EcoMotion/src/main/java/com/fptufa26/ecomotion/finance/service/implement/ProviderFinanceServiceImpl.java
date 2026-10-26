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
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.finance.dto.request.CreateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.request.PatchBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.request.UpdateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.response.BookingFinancialBreakdownResponse;
import com.fptufa26.ecomotion.finance.dto.response.DestinationBankSnapshotResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentEvidenceResponse;
import com.fptufa26.ecomotion.finance.dto.response.PaymentRequestResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderBankAccountResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderCommissionResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderCommissionSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderFinanceSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutDetailResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutSummaryResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderTransactionResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.enums.SupportedBank;
import com.fptufa26.ecomotion.finance.exception.BankAccountConflictException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountHolderNameException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountNumberException;
import com.fptufa26.ecomotion.finance.exception.InvalidBankCodeException;
import com.fptufa26.ecomotion.finance.exception.PayoutIneligibleException;
import com.fptufa26.ecomotion.finance.exception.PayoutNotFoundException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.service.ProviderFinanceService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProviderFinanceServiceImpl implements ProviderFinanceService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Pattern ACCOUNT_HOLDER_PATTERN = Pattern.compile("^[a-zA-ZÀ-ỹA-Z0-9\\s.'-]+$");
    private static final String DEFAULT_CURRENCY = "VND";

    private final UserService userService;
    private final ProviderBankAccountRepository providerBankAccountRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final BookingPaymentLedgerService bookingPaymentLedgerService;

    @Override
    public ProviderBankAccountResponse getBankAccount(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        Optional<ProviderBankAccount> activeOpt = providerBankAccountRepository.findByProvider_IdAndStatus(
                provider.getId(), BankAccountStatus.ACTIVE);

        if (activeOpt.isPresent()) {
            return toBankAccountResponse(activeOpt.get(), true);
        }

        if (provider.getBankAccountNo() != null && !provider.getBankAccountNo().isBlank()) {
            String bankCode = provider.getBankCode();
            if (bankCode == null || bankCode.isBlank()) {
                bankCode = SupportedBank.fromName(provider.getBankName())
                        .map(SupportedBank::getCode)
                        .orElse("OTHER");
            }
            return new ProviderBankAccountResponse(
                    null,
                    provider.getId(),
                    bankCode,
                    provider.getBankName(),
                    maskAccountNumber(provider.getBankAccountNo()),
                    maskAccountNumber(provider.getBankAccountNo()),
                    provider.getBankAccountHolder(),
                    BankAccountStatus.ACTIVE,
                    false,
                    provider.getUpdatedAt(),
                    provider.getUpdatedAt()
            );
        }

        return null;
    }

    @Override
    public List<ProviderBankAccountResponse> listBankAccounts(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        List<ProviderBankAccount> accounts = providerBankAccountRepository.findByProvider_IdOrderByCreatedAtDesc(provider.getId());

        if (accounts.isEmpty()) {
            ProviderBankAccountResponse fallback = getBankAccount(currentUserId);
            return fallback != null ? List.of(fallback) : List.of();
        }

        return accounts.stream()
                .map(a -> toBankAccountResponse(a, a.getStatus() == BankAccountStatus.ACTIVE))
                .toList();
    }

    @Override
    @Transactional
    public ProviderBankAccountResponse createBankAccount(Long currentUserId, CreateBankAccountRequest request) {
        Provider provider = resolveProvider(currentUserId);

        SupportedBank bank = validateAndResolveBank(request.bankCode(), request.accountNumber(), request.accountHolderName());
        String cleanNumber = request.accountNumber().trim();
        String cleanHolder = request.accountHolderName().trim().toUpperCase(Locale.ROOT);

        // Deactivate all current bank accounts for this provider
        List<ProviderBankAccount> existing = providerBankAccountRepository.findByProvider_IdOrderByCreatedAtDesc(provider.getId());
        for (ProviderBankAccount acc : existing) {
            acc.setStatus(BankAccountStatus.INACTIVE);
            providerBankAccountRepository.save(acc);
        }

        ProviderBankAccount pba = new ProviderBankAccount();
        pba.setProvider(provider);
        pba.setBankCode(bank.getCode());
        pba.setBankName(bank.getShortName());
        pba.setAccountNumber(cleanNumber);
        pba.setAccountHolderName(cleanHolder);
        pba.setStatus(BankAccountStatus.ACTIVE);
        pba.setCreatedAt(Instant.now());
        pba.setUpdatedAt(Instant.now());

        ProviderBankAccount saved = providerBankAccountRepository.save(pba);

        // Synchronize provider table snapshot columns
        userService.updateProviderBankSnapshot(provider.getId(), bank.getCode(), bank.getShortName(),
                cleanNumber, cleanHolder);

        log.info("Provider {} created active bank account #{} ({})", provider.getId(), saved.getId(), bank.getCode());
        return toBankAccountResponse(saved, true);
    }

    @Override
    @Transactional
    public ProviderBankAccountResponse patchBankAccount(Long currentUserId, Long bankAccountId, PatchBankAccountRequest request) {
        Provider provider = resolveProvider(currentUserId);

        ProviderBankAccount account = providerBankAccountRepository.findByIdAndProvider_Id(bankAccountId, provider.getId())
                .orElseThrow(() -> new BankAccountConflictException("Không tìm thấy tài khoản ngân hàng #" + bankAccountId));

        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            SupportedBank bank = SupportedBank.fromCode(request.bankCode())
                    .orElseThrow(() -> new InvalidBankCodeException("Mã ngân hàng '" + request.bankCode() + "' không hợp lệ"));
            account.setBankCode(bank.getCode());
            account.setBankName(bank.getShortName());
        }

        if (request.accountNumber() != null && !request.accountNumber().isBlank()) {
            String cleanNumber = request.accountNumber().trim();
            if (cleanNumber.contains("*")) {
                throw new InvalidAccountNumberException("Không được sử dụng số tài khoản đã che; vui lòng nhập số tài khoản đầy đủ");
            }
            SupportedBank bank = SupportedBank.fromCode(account.getBankCode())
                    .orElse(SupportedBank.VCB);
            if (!bank.isValidAccountNumber(cleanNumber)) {
                throw new InvalidAccountNumberException("Số tài khoản không hợp lệ đối với ngân hàng " + bank.getShortName());
            }
            account.setAccountNumber(cleanNumber);
        }

        if (request.accountHolderName() != null && !request.accountHolderName().isBlank()) {
            String cleanHolder = request.accountHolderName().trim().toUpperCase(Locale.ROOT);
            if (cleanHolder.length() < 2 || cleanHolder.length() > 150 || !ACCOUNT_HOLDER_PATTERN.matcher(cleanHolder).matches()) {
                throw new InvalidAccountHolderNameException("Tên chủ tài khoản không hợp lệ");
            }
            account.setAccountHolderName(cleanHolder);
        }

        if (request.status() != null) {
            if (request.status() == BankAccountStatus.ACTIVE) {
                // Deactivate others
                List<ProviderBankAccount> others = providerBankAccountRepository.findByProvider_IdOrderByCreatedAtDesc(provider.getId());
                for (ProviderBankAccount other : others) {
                    if (!other.getId().equals(account.getId())) {
                        other.setStatus(BankAccountStatus.INACTIVE);
                        providerBankAccountRepository.save(other);
                    }
                }
            }
            account.setStatus(request.status());
        }

        account.setUpdatedAt(Instant.now());
        ProviderBankAccount saved = providerBankAccountRepository.save(account);

        if (saved.getStatus() == BankAccountStatus.ACTIVE) {
            userService.updateProviderBankSnapshot(provider.getId(), saved.getBankCode(), saved.getBankName(),
                    saved.getAccountNumber(), saved.getAccountHolderName());
        }

        log.info("Provider {} patched bank account #{}", provider.getId(), saved.getId());
        return toBankAccountResponse(saved, saved.getStatus() == BankAccountStatus.ACTIVE);
    }

    @Override
    @Transactional
    public ProviderBankAccountResponse updateBankAccount(Long currentUserId, UpdateBankAccountRequest request) {
        CreateBankAccountRequest createRequest = new CreateBankAccountRequest(
                request.bankCode(), request.accountNumber(), request.accountHolderName());
        return createBankAccount(currentUserId, createRequest);
    }

    @Override
    public ProviderFinanceSummaryResponse getFinanceSummary(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        Long providerId = provider.getId();

        // 1. Confirmed incoming payments
        List<PaymentTransaction> allTx = bookingPaymentLedgerService.listProviderTransactions(providerId);
        BigDecimal confirmedIncoming = allTx.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Completed refunds
        BigDecimal completedRefunds = allTx.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT && t.getType() == PaymentType.REFUND)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalConfirmedRevenue = confirmedIncoming.subtract(completedRefunds).max(BigDecimal.ZERO);

        // 3. Pending customer payments (from active PaymentRequests)
        List<PaymentRequest> allPr = paymentRequestRepository.findByBooking_Vehicle_Provider_Id(providerId, Pageable.unpaged()).getContent();
        BigDecimal pendingPayments = allPr.stream()
                .filter(pr -> pr.getStatus() == PaymentRequestStatus.PENDING
                        || pr.getStatus() == PaymentRequestStatus.PROOF_SUBMITTED
                        || pr.getStatus() == PaymentRequestStatus.PARTIALLY_PAID)
                .map(pr -> pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Approved Surcharges across bookings
        List<Booking> providerBookings = bookingPaymentLedgerService.listProviderBookings(providerId);
        BigDecimal approvedSurcharges = BigDecimal.ZERO;
        for (Booking b : providerBookings) {
            List<BookingCharge> charges = bookingPaymentLedgerService.listCharges(b.getId());
            BigDecimal bookingCharges = charges.stream()
                    .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.DEPOSIT)
                    .map(BookingCharge::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            approvedSurcharges = approvedSurcharges.add(bookingCharges);
        }

        // 5. Commission calculation
        BigDecimal commissionRate = provider.getCommissionRate() != null ? provider.getCommissionRate() : BigDecimal.ZERO;
        BigDecimal estimatedCommission = totalConfirmedRevenue.multiply(commissionRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal netEarnings = totalConfirmedRevenue.subtract(estimatedCommission).max(BigDecimal.ZERO);

        long totalBookings = providerBookings.size();
        long pendingBookings = providerBookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.ACCEPTED).count();
        long completedBookings = providerBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();

        return new ProviderFinanceSummaryResponse(
                totalConfirmedRevenue.setScale(2, RoundingMode.HALF_UP),
                pendingPayments.setScale(2, RoundingMode.HALF_UP),
                approvedSurcharges.setScale(2, RoundingMode.HALF_UP),
                completedRefunds.setScale(2, RoundingMode.HALF_UP),
                estimatedCommission.setScale(2, RoundingMode.HALF_UP),
                netEarnings.setScale(2, RoundingMode.HALF_UP),
                totalBookings,
                pendingBookings,
                completedBookings
        );
    }

    @Override
    public PageResponse<ProviderTransactionResponse> listTransactions(Long currentUserId, String type, int offset, int limit) {
        Provider provider = resolveProvider(currentUserId);

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int pageIndex = safeOffset / safeLimit;
        Pageable pageable = PageRequest.of(pageIndex, safeLimit, DEFAULT_SORT);

        PaymentType filterType = parsePaymentType(type);

        Page<PaymentTransaction> page;
        if (filterType != null) {
            page = bookingPaymentLedgerService.pageProviderTransactions(provider.getId(), filterType, null, pageable);
        } else {
            page = bookingPaymentLedgerService.pageProviderTransactions(provider.getId(), null, null, pageable);
        }

        List<ProviderTransactionResponse> items = page.getContent().stream()
                .map(this::toTransactionResponse)
                .toList();

        boolean hasMore = (long) (pageIndex + 1) * safeLimit < page.getTotalElements();
        return new PageResponse<>(items, page.getTotalElements(), safeOffset, safeLimit, hasMore);
    }

    @Override
    public BookingFinancialBreakdownResponse getBookingBreakdown(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveBooking(provider, bookingCodeOrId);

        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        BookingPolicySnapshot snapshot = bookingPaymentLedgerService.findSnapshot(booking.getId()).orElse(null);
        BigDecimal depositRatio = snapshot != null && snapshot.getDepositRatio() != null
                ? snapshot.getDepositRatio()
                : (provider.getDepositRatio() != null ? provider.getDepositRatio() : new BigDecimal("0.3000"));

        BigDecimal depositRequired = snapshot != null && snapshot.getUpfrontAmount() != null && snapshot.getUpfrontAmount().compareTo(BigDecimal.ZERO) > 0
                ? snapshot.getUpfrontAmount()
                : baseRental.multiply(depositRatio).setScale(0, RoundingMode.HALF_UP);

        List<BookingCharge> charges = bookingPaymentLedgerService.listCharges(booking.getId());
        BigDecimal approvedCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal approvedReductions = charges.stream()
                .filter(BookingCharge::isWaived)
                .filter(c -> c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPayable = baseRental.add(approvedCharges).max(BigDecimal.ZERO);

        List<PaymentTransaction> transactions = bookingPaymentLedgerService.listTransactionsAscending(booking.getId());
        BigDecimal confirmedIncoming = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal allocatedPayments = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(t -> t.getAppliedAmount() != null && t.getAppliedAmount().signum() > 0
                        ? t.getAppliedAmount()
                        : t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal completedRefunds = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT && t.getType() == PaymentType.REFUND)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal depositPaid = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getType() == PaymentType.DEPOSIT_PAY)
                .map(t -> t.getAppliedAmount() != null && t.getAppliedAmount().signum() > 0
                        ? t.getAppliedAmount()
                        : t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netConfirmedPayments = confirmedIncoming.subtract(completedRefunds).max(BigDecimal.ZERO);
        BigDecimal remainingAmount = totalPayable.subtract(allocatedPayments).max(BigDecimal.ZERO);
        BigDecimal overpaidAmount = confirmedIncoming.subtract(completedRefunds).subtract(totalPayable).max(BigDecimal.ZERO);

        List<BookingChargeResponse> chargeResponses = charges.stream()
                .map(this::toChargeResponse)
                .toList();

        List<ProviderTransactionResponse> txResponses = transactions.stream()
                .map(this::toTransactionResponse)
                .toList();

        List<PaymentRequest> requests = paymentRequestRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId());
        List<PaymentRequestResponse> prResponses = requests.stream()
                .map(this::toPaymentRequestResponse)
                .toList();

        return new BookingFinancialBreakdownResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                baseRental.setScale(2, RoundingMode.HALF_UP),
                depositRequired.setScale(2, RoundingMode.HALF_UP),
                depositPaid.setScale(2, RoundingMode.HALF_UP),
                approvedCharges.setScale(2, RoundingMode.HALF_UP),
                approvedReductions.setScale(2, RoundingMode.HALF_UP),
                totalPayable.setScale(2, RoundingMode.HALF_UP),
                netConfirmedPayments.setScale(2, RoundingMode.HALF_UP),
                remainingAmount.setScale(2, RoundingMode.HALF_UP),
                overpaidAmount.setScale(2, RoundingMode.HALF_UP),
                chargeResponses,
                txResponses,
                prResponses
        );
    }

    @Override
    public ProviderCommissionSummaryResponse getCommissionSummary(Long currentUserId) {
        Provider provider = resolveProvider(currentUserId);
        BigDecimal rate = provider.getCommissionRate() != null ? provider.getCommissionRate() : BigDecimal.ZERO;

        List<Booking> completedBookings = bookingPaymentLedgerService.listProviderBookings(provider.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .toList();

        List<ProviderCommissionResponse> commissions = new ArrayList<>();
        BigDecimal totalCommissionDue = BigDecimal.ZERO;

        for (Booking b : completedBookings) {
            BigDecimal base = b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO;
            List<PaymentTransaction> txList = bookingPaymentLedgerService.listTransactions(b.getId());
            BigDecimal confirmedRev = txList.stream()
                    .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                    .map(PaymentTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal commAmt = confirmedRev.multiply(rate).setScale(0, RoundingMode.HALF_UP);
            totalCommissionDue = totalCommissionDue.add(commAmt);

            commissions.add(new ProviderCommissionResponse(
                    b.getId(),
                    b.getBookingCode(),
                    base,
                    confirmedRev,
                    rate,
                    commAmt,
                    "COMMISSION_DUE",
                    b.getCreatedAt()
            ));
        }

        return new ProviderCommissionSummaryResponse(
                totalCommissionDue.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), // Not automatically deducted in MVP
                rate,
                commissions
        );
    }

    // ==========================================
    // Legacy Payout Methods (Backward Compatibility)
    // ==========================================

    @Override
    public PageResponse<ProviderPayoutSummaryResponse> listPayouts(Long currentUserId, String status, int offset, int limit) {
        Provider provider = resolveProvider(currentUserId);

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int pageIndex = safeOffset / safeLimit;

        Pageable pageable = PageRequest.of(pageIndex, safeLimit, DEFAULT_SORT);
        PaymentStatus filterStatus = parsePaymentStatus(status);

        Page<PaymentTransaction> page;
        if (filterStatus != null) {
            page = bookingPaymentLedgerService.pageProviderTransactions(
                    provider.getId(), PaymentType.PAYOUT, filterStatus, pageable);
        } else {
            page = bookingPaymentLedgerService.pageProviderTransactions(
                    provider.getId(), PaymentType.PAYOUT, null, pageable);
        }

        List<ProviderPayoutSummaryResponse> items = page.getContent().stream()
                .map(this::toPayoutSummaryResponse)
                .toList();

        boolean hasMore = (long) (pageIndex + 1) * safeLimit < page.getTotalElements();
        return new PageResponse<>(items, page.getTotalElements(), safeOffset, safeLimit, hasMore);
    }

    @Override
    public ProviderPayoutDetailResponse getPayoutDetail(Long currentUserId, Long payoutId) {
        Provider provider = resolveProvider(currentUserId);

        PaymentTransaction pt = bookingPaymentLedgerService.findProviderTransaction(payoutId, provider.getId())
                .filter(tx -> tx.getType() == PaymentType.PAYOUT)
                .orElseThrow(() -> new PayoutNotFoundException("Không tìm thấy thông tin chi trả #" + payoutId));

        return toPayoutDetailResponse(pt);
    }

    @Override
    @Transactional
    public PaymentTransaction createMockPayout(Long bookingId) {
        Booking booking = bookingPaymentLedgerService.requireBooking(bookingId);

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new PayoutIneligibleException("Chỉ đơn thuê đã hoàn tất (COMPLETED) mới đủ điều kiện quyết toán chi trả");
        }

        boolean hasSuccessfulCustomerPayment = bookingPaymentLedgerService.listTransactions(bookingId).stream()
                .anyMatch(tx -> tx.getStatus() == PaymentStatus.PAID
                        && (tx.getType() == PaymentType.DEPOSIT_PAY
                        || tx.getType() == PaymentType.UPFRONT_PAY
                        || tx.getType() == PaymentType.SETTLEMENT));

        if (!hasSuccessfulCustomerPayment) {
            throw new PayoutIneligibleException("Đơn thuê chưa hoàn tất thanh toán từ phía khách hàng");
        }

        Provider provider = booking.getVehicle().getProvider();
        if (provider.getBankAccountNo() == null || provider.getBankAccountNo().isBlank()) {
            throw new PayoutIneligibleException("Nhà cung cấp chưa cấu hình tài khoản ngân hàng nhận tiền");
        }

        if (bookingPaymentLedgerService.transactionExists(bookingId, PaymentType.PAYOUT)) {
            return bookingPaymentLedgerService.findTransaction(bookingId, PaymentType.PAYOUT).orElseThrow();
        }

        BigDecimal grossAmount = calculateGrossAmount(booking);
        BigDecimal commissionRate = provider.getCommissionRate() != null ? provider.getCommissionRate() : BigDecimal.ZERO;
        BigDecimal platformFee = grossAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(platformFee);

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PayoutIneligibleException("Số tiền quyết toán chi trả không hợp lệ: " + netAmount);
        }

        String bankCode = provider.getBankCode();
        if (bankCode == null || bankCode.isBlank()) {
            bankCode = SupportedBank.fromName(provider.getBankName())
                    .map(SupportedBank::getCode)
                    .orElse("VCB");
        }

        PaymentTransaction payout = new PaymentTransaction();
        payout.setBooking(booking);
        payout.setType(PaymentType.PAYOUT);
        payout.setDirection(PaymentDirection.OUT);
        payout.setMethod(PaymentMethod.BANK_TRANSFER);
        payout.setAmount(netAmount);
        payout.setStatus(PaymentStatus.PAID);
        payout.setGatewayRef("MOCK-PAYOUT-" + booking.getBookingCode());
        payout.setPaidAt(Instant.now());
        payout.setReceiptUrl("https://cdn.ecomotion.test/receipt/payout-" + booking.getBookingCode() + ".pdf");
        payout.setVerificationMethod(PaymentVerificationMethod.MOCK_SIMULATED);

        payout.setBankCodeSnapshot(bankCode);
        payout.setBankNameSnapshot(provider.getBankName());
        payout.setBankAccountNoSnapshot(maskAccountNumber(provider.getBankAccountNo()));
        payout.setBankAccountHolderSnapshot(provider.getBankAccountHolder());

        return bookingPaymentLedgerService.saveTransaction(payout);
    }

    // ==========================================
    // Internal Helper Methods
    // ==========================================

    private Provider resolveProvider(Long currentUserId) {
        return userService.findProviderByUserId(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Người dùng hiện tại không có hồ sơ nhà cung cấp"));
    }

    private Booking resolveBooking(Provider provider, String bookingCodeOrId) {
        return bookingPaymentLedgerService.findProviderBooking(bookingCodeOrId, provider.getId())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy đơn thuê hoặc bạn không có quyền truy cập"));
    }

    private SupportedBank validateAndResolveBank(String bankCode, String accountNumber, String accountHolderName) {
        SupportedBank bank = SupportedBank.fromCode(bankCode)
                .orElseThrow(() -> new InvalidBankCodeException("Mã ngân hàng '" + bankCode + "' không hợp lệ hoặc chưa được hỗ trợ"));

        String cleanNumber = accountNumber != null ? accountNumber.trim() : "";
        if (cleanNumber.contains("*")) {
            throw new InvalidAccountNumberException("Không được sử dụng số tài khoản đã che; vui lòng nhập số tài khoản đầy đủ");
        }
        if (!bank.isValidAccountNumber(cleanNumber)) {
            throw new InvalidAccountNumberException("Số tài khoản không hợp lệ đối với ngân hàng " + bank.getShortName()
                    + " (yêu cầu từ " + bank.getMinAccountLength() + " đến " + bank.getMaxAccountLength() + " chữ số)");
        }

        String holder = accountHolderName != null ? accountHolderName.trim().toUpperCase(Locale.ROOT) : "";
        if (holder.length() < 2 || holder.length() > 150 || !ACCOUNT_HOLDER_PATTERN.matcher(holder).matches()) {
            throw new InvalidAccountHolderNameException("Tên chủ tài khoản không hợp lệ");
        }

        return bank;
    }

    private ProviderBankAccountResponse toBankAccountResponse(ProviderBankAccount a, boolean isDefault) {
        String masked = maskAccountNumber(a.getAccountNumber());
        return new ProviderBankAccountResponse(
                a.getId(),
                a.getProvider().getId(),
                a.getBankCode(),
                a.getBankName(),
                masked,
                masked,
                a.getAccountHolderName(),
                a.getStatus(),
                false,
                a.getCreatedAt(),
                a.getUpdatedAt()
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
                tx.getReceiptUrl() == null ? null : "/api/v1/payment-transactions/" + tx.getId() + "/refund-proof",
                tx.getProcessingNote(),
                tx.getPaidAt(),
                tx.getCreatedAt()
        );
    }

    private PaymentRequestResponse toPaymentRequestResponse(PaymentRequest pr) {
        BigDecimal remaining = pr.getAmount().subtract(pr.getPaidAmount()).max(BigDecimal.ZERO);
        String masked = maskAccountNumber(pr.getAccountNumberSnapshot());
        String qrUrl = null;
        try {
            String cleanAccount = pr.getAccountNumberSnapshot().trim().replace(" ", "");
            String cleanAmount = remaining.setScale(0, RoundingMode.HALF_UP).toPlainString();
            String encodedRef = URLEncoder.encode(pr.getTransferReference().trim(), StandardCharsets.UTF_8);
            String encodedHolder = URLEncoder.encode(pr.getAccountHolderSnapshot().trim().toUpperCase(Locale.ROOT), StandardCharsets.UTF_8);
            qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                    pr.getBankCodeSnapshot().trim(), cleanAccount, cleanAmount, encodedRef, encodedHolder);
        } catch (Exception ignored) {
        }

        List<PaymentEvidenceResponse> evidenceResponses = pr.getEvidences().stream()
                .map(e -> new PaymentEvidenceResponse(
                        e.getId(),
                        pr.getId(),
                        e.getProofImageUrl(),
                        e.getTransferredAmount(),
                        e.getTransactionReference(),
                        e.getTransferredAt(),
                        e.getNote(),
                        e.getSubmittedBy() != null ? e.getSubmittedBy().getId() : null,
                        e.getSubmittedBy() != null ? e.getSubmittedBy().getFullName() : "",
                        e.getStatus(),
                        e.getCreatedAt(),
                        e.getReviewedAt()
                ))
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
                masked,
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

    private BookingChargeResponse toChargeResponse(BookingCharge charge) {
        return new BookingChargeResponse(
                charge.getId(),
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount(),
                charge.isWaived(),
                charge.getEvidenceUrl(),
                charge.getCreatedBy() != null ? charge.getCreatedBy().getFullName() : null,
                charge.getCreatedAt()
        );
    }

    private ProviderPayoutSummaryResponse toPayoutSummaryResponse(PaymentTransaction pt) {
        Booking booking = pt.getBooking();
        BigDecimal grossAmount = calculateGrossAmount(booking);
        BigDecimal netAmount = pt.getAmount();
        BigDecimal platformFee = grossAmount.subtract(netAmount).max(BigDecimal.ZERO);

        DestinationBankSnapshotResponse bankSnapshot = new DestinationBankSnapshotResponse(
                pt.getBankCodeSnapshot(),
                pt.getBankNameSnapshot(),
                pt.getBankAccountNoSnapshot(),
                pt.getBankAccountHolderSnapshot()
        );

        return new ProviderPayoutSummaryResponse(
                pt.getId(),
                booking.getBookingCode(),
                grossAmount,
                platformFee,
                netAmount,
                DEFAULT_CURRENCY,
                pt.getStatus(),
                pt.getMethod(),
                pt.getGatewayRef(),
                bankSnapshot,
                pt.getCreatedAt(),
                pt.getPaidAt()
        );
    }

    private ProviderPayoutDetailResponse toPayoutDetailResponse(PaymentTransaction pt) {
        Booking booking = pt.getBooking();
        BigDecimal grossAmount = calculateGrossAmount(booking);
        BigDecimal netAmount = pt.getAmount();
        BigDecimal platformFee = grossAmount.subtract(netAmount).max(BigDecimal.ZERO);

        String vehicleName = booking.getVehicle() != null
                ? booking.getVehicle().getBrand() + " " + booking.getVehicle().getModel()
                : "";
        String licensePlate = booking.getVehicle() != null
                ? booking.getVehicle().getLicensePlate()
                : "";

        DestinationBankSnapshotResponse bankSnapshot = new DestinationBankSnapshotResponse(
                pt.getBankCodeSnapshot(),
                pt.getBankNameSnapshot(),
                pt.getBankAccountNoSnapshot(),
                pt.getBankAccountHolderSnapshot()
        );

        return new ProviderPayoutDetailResponse(
                pt.getId(),
                booking.getBookingCode(),
                vehicleName,
                licensePlate,
                grossAmount,
                platformFee,
                netAmount,
                DEFAULT_CURRENCY,
                pt.getStatus(),
                pt.getMethod(),
                pt.getGatewayRef(),
                pt.getReceiptUrl(),
                bankSnapshot,
                pt.getCreatedAt(),
                pt.getPaidAt(),
                true
        );
    }

    private BigDecimal calculateGrossAmount(Booking booking) {
        List<BookingCharge> charges = bookingPaymentLedgerService.listCharges(booking.getId());
        BigDecimal sum = charges.stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.DEPOSIT)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            return sum.setScale(2, RoundingMode.HALF_UP);
        }
        return booking.getTotalAmount() != null
                ? booking.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private PaymentType parsePaymentType(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type.trim())) {
            return null;
        }
        try {
            return PaymentType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PaymentStatus parsePaymentStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
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
}
