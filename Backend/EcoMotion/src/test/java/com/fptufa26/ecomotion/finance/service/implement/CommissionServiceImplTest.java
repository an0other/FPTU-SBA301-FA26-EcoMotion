package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.dispute.service.DisputeQueryService;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmCommissionPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.CommissionStatement;
import com.fptufa26.ecomotion.finance.enums.CommissionStatementStatus;
import com.fptufa26.ecomotion.finance.repository.CommissionStatementLineRepository;
import com.fptufa26.ecomotion.finance.repository.CommissionStatementRepository;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommissionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");

    private final CommissionStatementRepository statements = mock(CommissionStatementRepository.class);
    private final CommissionStatementLineRepository lines = mock(CommissionStatementLineRepository.class);
    private final BookingPaymentLedgerService ledger = mock(BookingPaymentLedgerService.class);
    private final UserService users = mock(UserService.class);
    private final DisputeQueryService disputes = mock(DisputeQueryService.class);
    private final com.fptufa26.ecomotion.finance.repository.CommissionPaymentRepository payments =
            mock(com.fptufa26.ecomotion.finance.repository.CommissionPaymentRepository.class);
    private final List<CommissionStatement> saved = new ArrayList<>();
    private CommissionServiceImpl service;
    private Provider provider;

    @BeforeEach
    void setUp() {
        service = new CommissionServiceImpl(statements, lines, ledger, users, disputes,
                mock(NotificationStreamService.class), Clock.fixed(NOW, ZoneOffset.UTC), payments,
                mock(com.fptufa26.ecomotion.common.storage.service.FileStorageService.class));
        when(statements.findByProvider_IdOrderByPeriodMonthDesc(7L)).thenAnswer(inv -> List.copyOf(saved));
        ReflectionTestUtils.setField(service, "graceDays", 3);
        ReflectionTestUtils.setField(service, "bankCode", "VCB");
        ReflectionTestUtils.setField(service, "bankAccount", "1029384756");
        ReflectionTestUtils.setField(service, "bankHolder", "CONG TY ECOMOTION");
        provider = new Provider();
        provider.setId(7L);
        provider.setDisplayName("Depot A");
        provider.setCommissionRate(new BigDecimal("0.1000"));
        when(users.listProviders(ProviderStatus.APPROVED)).thenReturn(List.of(provider));
        when(statements.findByProvider_IdAndPeriodMonth(anyLong(), anyString()))
                .thenAnswer(inv -> saved.stream().filter(s -> s.getPeriodMonth().equals(inv.getArgument(1))).findFirst());
        when(statements.save(any())).thenAnswer(inv -> { CommissionStatement s = inv.getArgument(0); if (!saved.contains(s)) saved.add(s); return s; });
        when(lines.findBilledBookingIds(anyCollection())).thenReturn(Set.of());
    }

    private Booking completed(long id, String endedAt, String paid) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setBookingCode("BK-" + id);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualEndAt(Instant.parse(endedAt));
        PaymentTransaction tx = new PaymentTransaction();
        tx.setDirection(PaymentDirection.IN);
        tx.setStatus(PaymentStatus.PAID);
        tx.setType(PaymentType.SETTLEMENT);
        tx.setAmount(new BigDecimal(paid));
        when(ledger.listTransactions(id)).thenReturn(List.of(tx));
        return booking;
    }

    @Test
    void octoberStatementTaxesOnlyOctoberTripsAndDefersDisputedOnes() {
        Booking inOctober = completed(1, "2026-10-20T05:00:00Z", "3000000");
        Booking lastMinute = completed(2, "2026-10-31T16:30:00Z", "1000000");
        Booking november = completed(3, "2026-11-02T05:00:00Z", "9000000");
        Booking disputed = completed(4, "2026-10-10T05:00:00Z", "5000000");
        when(disputes.hasActiveDispute(4L)).thenReturn(true);
        when(ledger.listProviderBookings(7L)).thenReturn(List.of(inOctober, lastMinute, november, disputed));

        assertThat(service.generate(YearMonth.of(2026, 10))).isEqualTo(1);
        CommissionStatement october = saved.getFirst();
        assertThat(october.getBookingCount()).isEqualTo(2);
        assertThat(october.getGrossRevenue()).isEqualByComparingTo("4000000");
        assertThat(october.getCommissionAmount()).isEqualByComparingTo("400000");
        assertThat(october.getDueAt()).isEqualTo(Instant.parse("2026-11-05T10:00:00Z"));
        assertThat(october.getStatus()).as("đã qua hạn 5/11 lúc lập").isEqualTo(CommissionStatementStatus.OVERDUE);

        assertThat(service.generate(YearMonth.of(2026, 10))).as("chạy lại không tạo trùng").isZero();
        assertThatThrownBy(() -> service.generate(YearMonth.of(2026, 11))).as("tháng chưa kết thúc").isInstanceOf(RuntimeException.class);
    }

    @Test
    void refundsReduceRevenueButOverpaymentRefundsDoNot() {
        PaymentTransaction paid = new PaymentTransaction();
        paid.setDirection(PaymentDirection.IN); paid.setStatus(PaymentStatus.PAID); paid.setType(PaymentType.SETTLEMENT);
        paid.setAmount(new BigDecimal("1000000"));
        PaymentTransaction refund = new PaymentTransaction();
        refund.setDirection(PaymentDirection.OUT); refund.setStatus(PaymentStatus.PAID); refund.setType(PaymentType.REFUND);
        refund.setAmount(new BigDecimal("200000"));
        PaymentTransaction excessRefund = new PaymentTransaction();
        excessRefund.setDirection(PaymentDirection.OUT); excessRefund.setStatus(PaymentStatus.PAID); excessRefund.setType(PaymentType.REFUND);
        excessRefund.setAmount(new BigDecimal("50000")); excessRefund.setProcessingNote("EXCESS: khách chuyển dư");
        assertThat(CommissionServiceImpl.revenueOf(List.of(paid, refund, excessRefund))).isEqualByComparingTo("800000");
    }

    @Test
    void partialConfirmationKeepsDebtAndOnlyAdminMayConfirm() {
        CommissionStatement statement = new CommissionStatement();
        statement.setId(5L);
        statement.setProvider(provider);
        statement.setPeriodMonth("2026-10");
        statement.setDueAt(Instant.parse("2026-11-05T10:00:00Z"));
        statement.setCommissionAmount(new BigDecimal("400000"));
        statement.setStatus(CommissionStatementStatus.OVERDUE);
        when(statements.findById(5L)).thenReturn(Optional.of(statement));
        User admin = new User(); admin.setId(1L); admin.setRole(Role.ADMIN);
        User providerUser = new User(); providerUser.setId(2L); providerUser.setRole(Role.PROVIDER);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(providerUser));

        assertThatThrownBy(() -> service.confirmPayment(2L, 5L, new ConfirmCommissionPaymentRequest(new BigDecimal("400000"), null)))
                .isInstanceOf(AccessDeniedException.class);

        var partial = service.confirmPayment(1L, 5L, new ConfirmCommissionPaymentRequest(new BigDecimal("300000"), "Nhận 300k"));
        assertThat(partial.status()).isEqualTo(CommissionStatementStatus.OVERDUE);
        assertThat(partial.remainingAmount()).isEqualByComparingTo("100000");
        assertThat(partial.blocking()).as("quá hạn 6 ngày > 3 ngày ân hạn").isTrue();

        var full = service.confirmPayment(1L, 5L, new ConfirmCommissionPaymentRequest(new BigDecimal("100000"), null));
        assertThat(full.status()).isEqualTo(CommissionStatementStatus.PAID);
        assertThat(full.blocking()).isFalse();
        assertThat(full.transferContent()).isEqualTo("HH-7-202610");
    }

    @Test
    void reportedPaymentPausesLockWhileAdminReviews() {
        org.mockito.ArgumentCaptor<java.util.Collection<CommissionStatementStatus>> statuses = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        when(statements.findProvidersOverdueBefore(statuses.capture(), any())).thenReturn(List.of());
        service.blockedProviderIds();
        assertThat(statuses.getValue()).as("chỉ bảng kê chưa nộp mới khoá; đã báo chuyển được tạm mở, admin từ chối thì khoá lại")
                .containsExactlyInAnyOrder(CommissionStatementStatus.ISSUED, CommissionStatementStatus.OVERDUE);
    }

    @Test
    void earlierMonthWithoutStatementIsNotSweptIntoLaterPeriod() {
        Booking september = completed(11, "2026-09-20T05:00:00Z", "2000000");
        Booking october = completed(12, "2026-10-20T05:00:00Z", "3000000");
        when(ledger.listProviderBookings(7L)).thenReturn(List.of(september, october));

        service.generate(YearMonth.of(2026, 10));
        assertThat(saved.getFirst().getBookingCount()).as("tháng 9 chưa lập bảng kê nên chuyến tháng 9 không vào kỳ 10").isEqualTo(1);
        assertThat(saved.getFirst().getGrossRevenue()).isEqualByComparingTo("3000000");
        when(statements.findByProvider_IdOrderByPeriodMonthDesc(7L)).thenReturn(List.copyOf(saved));
        service.generate(YearMonth.of(2026, 9));
        assertThat(saved).hasSize(2);
        assertThat(saved.get(1).getGrossRevenue()).as("chuyến tháng 9 vào đúng bảng kê tháng 9").isEqualByComparingTo("2000000");
    }

    @Test
    void lockAppliesOnlyAfterGraceDays() {
        when(statements.findProvidersOverdueBefore(anyCollection(), eq(NOW.minusSeconds(3 * 86400L)))).thenReturn(List.of(7L));
        assertThat(service.isBookingBlocked(7L)).isTrue();
        assertThat(service.isBookingBlocked(8L)).isFalse();
    }

    private CommissionStatement statement(long id, String month, String commission, CommissionStatementStatus status) {
        CommissionStatement st = new CommissionStatement();
        st.setId(id);
        st.setProvider(provider);
        st.setPeriodMonth(month);
        st.setDueAt(Instant.parse("2026-11-05T10:00:00Z"));
        st.setCommissionAmount(new BigDecimal(commission));
        st.setStatus(status);
        saved.add(st);
        when(statements.findById(id)).thenReturn(Optional.of(st));
        return st;
    }

    private User admin() {
        User admin = new User(); admin.setId(1L); admin.setRole(Role.ADMIN);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void underpaymentIsShownAsPartialNotJustOverdue() {
        admin();
        statement(5L, "2026-10", "800000", CommissionStatementStatus.OVERDUE);
        var partial = service.confirmPayment(1L, 5L, new ConfirmCommissionPaymentRequest(new BigDecimal("500000"), null));
        assertThat(partial.paymentState()).isEqualTo("PARTIAL");
        assertThat(partial.remainingAmount()).isEqualByComparingTo("300000");
        assertThat(partial.payments()).hasSize(1);
        assertThat(partial.payments().getFirst().confirmedAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void overpaymentIsCreditedToTheNextOpenStatement() {
        admin();
        CommissionStatement september = statement(5L, "2026-09", "800000", CommissionStatementStatus.OVERDUE);
        CommissionStatement october = statement(6L, "2026-10", "300000", CommissionStatementStatus.ISSUED);
        var paid = service.confirmPayment(1L, 5L, new ConfirmCommissionPaymentRequest(new BigDecimal("1000000"), null));
        assertThat(paid.paymentState()).isEqualTo("OVERPAID");
        assertThat(paid.overpaidAmount()).isEqualByComparingTo("200000");
        assertThat(october.credit()).as("200k dư của tháng 9 trừ vào tháng 10").isEqualByComparingTo("200000");
        assertThat(october.remainingAmount()).isEqualByComparingTo("100000");
        assertThat(CommissionServiceImpl.availableCredit(List.of(september, october))).isZero();
    }
}
