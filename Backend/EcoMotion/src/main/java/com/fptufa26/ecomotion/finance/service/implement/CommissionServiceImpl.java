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
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.request.ReportCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.dto.response.CommissionOverviewResponse;
import com.fptufa26.ecomotion.finance.dto.response.CommissionStatementResponse;
import com.fptufa26.ecomotion.finance.entity.CommissionStatement;
import com.fptufa26.ecomotion.finance.entity.CommissionStatementLine;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;
import com.fptufa26.ecomotion.finance.repository.CommissionStatementLineRepository;
import com.fptufa26.ecomotion.finance.repository.CommissionStatementRepository;
import com.fptufa26.ecomotion.finance.service.CommissionService;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hoa hồng tháng: doanh thu thực nhận của các chuyến hoàn tất trong kỳ × tỉ lệ hoa hồng của nhà xe.
 * Kỳ = tháng dương lịch (giờ Việt Nam); hạn nộp 17:00 ngày 5 tháng sau; quá hạn {@code graceDays} ngày thì khoá nhận đơn mới.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<CommissionStatementStatus> UNPAID = Set.of(
            CommissionStatementStatus.ISSUED, CommissionStatementStatus.OVERDUE);

    private final CommissionStatementRepository statements;
    private final CommissionStatementLineRepository lines;
    private final BookingPaymentLedgerService ledger;
    private final UserService userService;
    private final DisputeQueryService disputeQueries;
    private final NotificationStreamService notifications;
    private final Clock clock;

    @Value("${app.commission.grace-days:3}") private int graceDays;
    @Value("${app.platform.bank.code:VCB}") private String bankCode;
    @Value("${app.platform.bank.name:Vietcombank}") private String bankName;
    @Value("${app.platform.bank.account:1029384756}") private String bankAccount;
    @Value("${app.platform.bank.holder:CONG TY ECOMOTION}") private String bankHolder;

    // ------------------------------------------------------------------ nhà xe

    @Override
    @Transactional(readOnly = true)
    public CommissionOverviewResponse overview(Long providerUserId) {
        Provider provider = requireProvider(providerUserId);
        YearMonth current = YearMonth.now(clock.withZone(VIETNAM));
        List<Eligible> pending = eligibleBookings(provider, clock.instant());
        BigDecimal rate = rateOf(provider);
        BigDecimal revenue = pending.stream().map(Eligible::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commission = pending.stream().map(e -> commissionOf(e.revenue(), rate)).reduce(BigDecimal.ZERO, BigDecimal::add);
        CommissionStatementResponse currentPeriod = new CommissionStatementResponse(
                null, provider.getId(), provider.getDisplayName(), current.toString(), periodStart(current), periodEnd(current),
                dueAt(current), revenue, rate, commission, BigDecimal.ZERO, commission, pending.size(), null, 0, false,
                null, null, null, null, null, null, null,
                pending.stream().map(e -> new CommissionStatementResponse.Line(e.booking().getId(),
                        e.booking().getBookingCode(), e.completedAt(), e.revenue(), commissionOf(e.revenue(), rate))).toList());
        List<CommissionStatementResponse> issued = statements.findByProvider_IdOrderByPeriodMonthDesc(provider.getId())
                .stream().map(s -> toResponse(s, false)).toList();
        BigDecimal outstanding = issued.stream()
                .filter(s -> s.status() != CommissionStatementStatus.PAID)
                .map(CommissionStatementResponse::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CommissionOverviewResponse(currentPeriod, issued, isBookingBlocked(provider.getId()), outstanding,
                new CommissionOverviewResponse.PlatformBank(bankCode, bankName, bankAccount, bankHolder), graceDays);
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionStatementResponse providerStatement(Long providerUserId, Long statementId) {
        return toResponse(requireOwned(providerUserId, statementId), true);
    }

    @Override
    @Transactional
    public CommissionStatementResponse reportPayment(Long providerUserId, Long statementId, ReportCommissionPaymentRequest request) {
        CommissionStatement statement = requireOwned(providerUserId, statementId);
        if (!UNPAID.contains(statement.getStatus()) || statement.remainingAmount().signum() <= 0) {
            throw new ConflictException("Bảng kê này không còn khoản hoa hồng cần nộp");
        }
        statement.setStatus(CommissionStatementStatus.PAYMENT_REPORTED);
        statement.setReportedAt(clock.instant());
        statement.setPaymentReference(blankToNull(request == null ? null : request.paymentReference()));
        statement.setReportedNote(blankToNull(request == null ? null : request.note()));
        statements.save(statement);
        notifyProvider(statement, "COMMISSION_PAYMENT_REPORTED");
        return toResponse(statement, true);
    }

    // ------------------------------------------------------------------ admin

    @Override
    @Transactional(readOnly = true)
    public List<CommissionStatementResponse> adminStatements(String periodMonth, CommissionStatementStatus status) {
        List<CommissionStatement> rows = periodMonth == null || periodMonth.isBlank()
                ? statements.findAllByOrderByPeriodMonthDescIdAsc()
                : statements.findByPeriodMonthOrderByIdAsc(YearMonth.parse(periodMonth.trim()).toString());
        return rows.stream().filter(s -> status == null || s.getStatus() == status)
                .map(s -> toResponse(s, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionStatementResponse adminStatement(Long statementId) {
        return toResponse(require(statementId), true);
    }

    @Override
    @Transactional
    public CommissionStatementResponse confirmPayment(Long adminUserId, Long statementId, ConfirmCommissionPaymentRequest request) {
        User admin = userService.findById(adminUserId)
                .filter(u -> u.getRole() == com.fptufa26.ecomotion.user.enums.Role.ADMIN)
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được xác nhận hoa hồng"));
        CommissionStatement statement = require(statementId);
        if (statement.getStatus() == CommissionStatementStatus.PAID) {
            throw new ConflictException("Bảng kê đã được xác nhận nộp đủ");
        }
        BigDecimal received = request.receivedAmount().setScale(2, RoundingMode.HALF_UP);
        statement.setPaidAmount(statement.getPaidAmount().add(received));
        statement.setConfirmedAt(clock.instant());
        statement.setConfirmedBy(admin);
        statement.setAdminNote(blankToNull(request.note()));
        if (statement.remainingAmount().signum() <= 0) {
            statement.setStatus(CommissionStatementStatus.PAID);
        } else {
            // Nhận thiếu: còn nợ phần chênh, trạng thái theo hạn nộp.
            statement.setStatus(statement.getDueAt().isBefore(clock.instant())
                    ? CommissionStatementStatus.OVERDUE : CommissionStatementStatus.ISSUED);
        }
        statements.save(statement);
        notifyProvider(statement, "COMMISSION_CONFIRMED");
        return toResponse(statement, true);
    }

    @Override
    @Transactional
    public CommissionStatementResponse rejectReport(Long statementId, String note) {
        CommissionStatement statement = require(statementId);
        if (statement.getStatus() != CommissionStatementStatus.PAYMENT_REPORTED) {
            throw new ConflictException("Bảng kê không ở trạng thái chờ xác nhận");
        }
        statement.setStatus(statement.getDueAt().isBefore(clock.instant())
                ? CommissionStatementStatus.OVERDUE : CommissionStatementStatus.ISSUED);
        statement.setAdminNote(blankToNull(note));
        statements.save(statement);
        notifyProvider(statement, "COMMISSION_REPORT_REJECTED");
        return toResponse(statement, true);
    }

    // ------------------------------------------------------------------ job

    @Override
    @Transactional
    public int generate(YearMonth month) {
        YearMonth current = YearMonth.now(clock.withZone(VIETNAM));
        if (!month.isBefore(current)) {
            throw new ConflictException("Chỉ lập bảng kê cho tháng đã kết thúc");
        }
        Instant end = periodEnd(month);
        int created = 0;
        for (Provider provider : userService.listProviders(ProviderStatus.APPROVED)) {
            if (statements.findByProvider_IdAndPeriodMonth(provider.getId(), month.toString()).isPresent()) continue;
            List<Eligible> due = eligibleBookings(provider, end);
            if (due.isEmpty()) continue;
            BigDecimal rate = rateOf(provider);
            CommissionStatement statement = new CommissionStatement();
            statement.setProvider(provider);
            statement.setPeriodMonth(month.toString());
            statement.setPeriodStart(periodStart(month));
            statement.setPeriodEnd(end);
            statement.setDueAt(dueAt(month));
            statement.setCommissionRate(rate);
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal commission = BigDecimal.ZERO;
            for (Eligible e : due) {
                CommissionStatementLine line = new CommissionStatementLine();
                line.setStatement(statement);
                line.setBooking(e.booking());
                line.setCompletedAt(e.completedAt());
                line.setRevenue(e.revenue());
                line.setCommission(commissionOf(e.revenue(), rate));
                statement.getLines().add(line);
                revenue = revenue.add(e.revenue());
                commission = commission.add(line.getCommission());
            }
            statement.setGrossRevenue(revenue);
            statement.setCommissionAmount(commission);
            statement.setBookingCount(due.size());
            statement.setStatus(commission.signum() <= 0 ? CommissionStatementStatus.PAID
                    : statement.getDueAt().isBefore(clock.instant()) ? CommissionStatementStatus.OVERDUE
                    : CommissionStatementStatus.ISSUED);
            statements.save(statement);
            notifyProvider(statement, "COMMISSION_ISSUED");
            created++;
        }
        log.info("Lập {} bảng kê hoa hồng cho kỳ {}", created, month);
        return created;
    }

    @Override
    @Transactional
    public int markOverdue() {
        int changed = 0;
        for (CommissionStatement statement : statements.findByStatusInAndDueAtBefore(
                Set.of(CommissionStatementStatus.ISSUED), clock.instant())) {
            statement.setStatus(CommissionStatementStatus.OVERDUE);
            statements.save(statement);
            notifyProvider(statement, "COMMISSION_OVERDUE");
            changed++;
        }
        return changed;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBookingBlocked(Long providerId) {
        return providerId != null && blockedProviderIds().contains(providerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> blockedProviderIds() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(graceDays));
        return new HashSet<>(statements.findProvidersOverdueBefore(UNPAID, cutoff));
    }

    // ------------------------------------------------------------------ tính toán

    private record Eligible(Booking booking, Instant completedAt, BigDecimal revenue) {
    }

    /**
     * Đơn hoàn tất trước {@code cutoff}, chưa nằm trong bảng kê nào và không còn khiếu nại mở
     * (đơn đang khiếu nại được dời sang kỳ sau). Doanh thu = tiền thực nhận áp vào đơn − tiền đã hoàn;
     * không tính tiền bảo đảm gói năm và phần khách chuyển dư.
     */
    private List<Eligible> eligibleBookings(Provider provider, Instant cutoff) {
        List<Booking> completed = ledger.listProviderBookings(provider.getId()).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .filter(b -> completedAt(b).isBefore(cutoff))
                .toList();
        if (completed.isEmpty()) return List.of();
        Set<Long> billed = lines.findBilledBookingIds(completed.stream().map(Booking::getId).toList());
        List<Eligible> result = new ArrayList<>();
        for (Booking booking : completed) {
            if (billed.contains(booking.getId()) || disputeQueries.hasActiveDispute(booking.getId())) continue;
            BigDecimal revenue = revenueOf(ledger.listTransactions(booking.getId()));
            if (revenue.signum() > 0) result.add(new Eligible(booking, completedAt(booking), revenue));
        }
        result.sort(Comparator.comparing(Eligible::completedAt));
        return result;
    }

    static BigDecimal revenueOf(List<PaymentTransaction> transactions) {
        BigDecimal received = transactions.stream()
                .filter(t -> t.getDirection() == PaymentDirection.IN && t.getStatus() == PaymentStatus.PAID
                        && t.getType() != PaymentType.SECURITY_DEPOSIT)
                .map(t -> RefundMath.appliedPortion(t.getAmount(), t.getAppliedAmount(), t.getProcessingNote()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = transactions.stream()
                .filter(t -> t.getType() == PaymentType.REFUND && t.getDirection() == PaymentDirection.OUT
                        && t.getStatus() == PaymentStatus.PAID && !RefundMath.excess(t.getProcessingNote()))
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return received.subtract(refunded).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    static Instant completedAt(Booking booking) {
        if (booking.getActualEndAt() != null) return booking.getActualEndAt();
        return booking.getUpdatedAt() != null ? booking.getUpdatedAt() : Instant.EPOCH;
    }

    private static BigDecimal commissionOf(BigDecimal revenue, BigDecimal rate) {
        return revenue.multiply(rate).setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rateOf(Provider provider) {
        return provider.getCommissionRate() == null ? BigDecimal.ZERO : provider.getCommissionRate();
    }

    static Instant periodStart(YearMonth month) {
        return month.atDay(1).atStartOfDay(VIETNAM).toInstant();
    }

    static Instant periodEnd(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(VIETNAM).toInstant();
    }

    static Instant dueAt(YearMonth month) {
        return month.plusMonths(1).atDay(5).atTime(17, 0).atZone(VIETNAM).toInstant();
    }

    // ------------------------------------------------------------------ helpers

    private CommissionStatementResponse toResponse(CommissionStatement s, boolean withLines) {
        Instant now = clock.instant();
        boolean unpaid = UNPAID.contains(s.getStatus());
        long overdueDays = unpaid && s.getDueAt().isBefore(now) ? Duration.between(s.getDueAt(), now).toDays() : 0;
        boolean blocking = unpaid && s.getDueAt().plus(Duration.ofDays(graceDays)).isBefore(now);
        String content = "HH-" + s.getProvider().getId() + "-" + s.getPeriodMonth().replace("-", "");
        return new CommissionStatementResponse(
                s.getId(), s.getProvider().getId(), s.getProvider().getDisplayName(), s.getPeriodMonth(),
                s.getPeriodStart(), s.getPeriodEnd(), s.getDueAt(), s.getGrossRevenue(), s.getCommissionRate(),
                s.getCommissionAmount(), s.getPaidAmount(), s.remainingAmount(), s.getBookingCount(), s.getStatus(),
                overdueDays, blocking, s.getReportedAt(), s.getPaymentReference(), s.getReportedNote(), s.getConfirmedAt(),
                s.getAdminNote(), content, qrUrl(s.remainingAmount(), content),
                withLines ? s.getLines().stream().map(l -> new CommissionStatementResponse.Line(
                        l.getBooking().getId(), l.getBooking().getBookingCode(), l.getCompletedAt(),
                        l.getRevenue(), l.getCommission())).toList() : List.of());
    }

    private String qrUrl(BigDecimal amount, String content) {
        if (amount.signum() <= 0) return null;
        return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                bankCode, bankAccount, amount.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                URLEncoder.encode(content, StandardCharsets.UTF_8), URLEncoder.encode(bankHolder, StandardCharsets.UTF_8));
    }

    private void notifyProvider(CommissionStatement statement, String event) {
        try {
            notifications.sendEventToProvider(statement.getProvider().getId(), event, Map.of(
                    "statementId", statement.getId() == null ? 0 : statement.getId(),
                    "periodMonth", statement.getPeriodMonth(),
                    "status", statement.getStatus().name()));
        } catch (RuntimeException ex) {
            log.debug("Không gửi được sự kiện hoa hồng: {}", ex.getMessage());
        }
    }

    private Provider requireProvider(Long providerUserId) {
        return userService.findProviderByUserId(providerUserId)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không phải nhà xe"));
    }

    private CommissionStatement require(Long id) {
        return statements.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy bảng kê hoa hồng #" + id));
    }

    private CommissionStatement requireOwned(Long providerUserId, Long statementId) {
        Provider provider = requireProvider(providerUserId);
        CommissionStatement statement = require(statementId);
        if (!statement.getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Bảng kê không thuộc nhà xe của bạn");
        }
        return statement;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
