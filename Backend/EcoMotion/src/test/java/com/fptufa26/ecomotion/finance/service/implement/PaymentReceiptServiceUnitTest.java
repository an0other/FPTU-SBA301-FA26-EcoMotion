package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.finance.enums.PaymentReceiptStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceiptServiceUnitTest {
    @Mock BookingPaymentLedgerService ledger;
    @Mock PaymentRequestRepository paymentRequests;
    @Mock UserService users;
    @InjectMocks PaymentReceiptServiceImpl service;
    PaymentTransaction payment;

    @BeforeEach
    void fixture() {
        User owner = new User(); owner.setId(1L); owner.setRole(Role.RENTER); owner.setStatus(UserStatus.ACTIVE);
        Renter renter = new Renter(); renter.setUser(owner);
        Booking booking = new Booking(); booking.setBookingCode("RECEIPT"); booking.setRenter(renter);
        payment = new PaymentTransaction(); payment.setBooking(booking); payment.setDirection(PaymentDirection.IN);
        payment.setType(PaymentType.DEPOSIT_PAY); payment.setMethod(PaymentMethod.VNPAY); payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("30000")); payment.setAppliedAmount(new BigDecimal("30000"));
        payment.setPaidAt(Instant.now()); payment.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
        lenient().when(users.findById(1L)).thenReturn(Optional.of(owner));
        lenient().when(ledger.findTransactionByReference("REF")).thenReturn(Optional.of(payment));
    }

    @Test
    void anonymousReceiptCannotReadLedger() {
        assertThatThrownBy(() -> service.getReceipt(null, "REF")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(ledger, paymentRequests);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void storedStatusDeterminesReceipt(PaymentStatus status) {
        payment.setStatus(status);
        var expected = switch (status) {
            case PAID -> PaymentReceiptStatus.SUCCESS;
            case FAILED -> PaymentReceiptStatus.FAILED;
            case PENDING -> PaymentReceiptStatus.PENDING_VERIFICATION;
        };
        assertThat(service.getReceipt(1L, "REF").status()).isEqualTo(expected);
    }

    @Test
    void zeroAllocationOrReconciliationNeverProducesSuccess() {
        payment.setAppliedAmount(BigDecimal.ZERO);
        assertThat(service.getReceipt(1L, "REF").status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
        payment.setAppliedAmount(payment.getAmount()); payment.setProcessingNote("RECONCILIATION_REQUIRED: hold expired");
        assertThat(service.getReceipt(1L, "REF").status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentVerificationMethod.class, names = {"MOCK_SIMULATED", "MANUAL_UNVERIFIED", "PROVIDER_CONFIRMED"})
    void vnpayRequiresGatewayVerification(PaymentVerificationMethod method) {
        payment.setVerificationMethod(method);
        assertThat(service.getReceipt(1L, "REF").status()).isEqualTo(PaymentReceiptStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    void inactiveOwnerCannotReadReceipt() {
        User inactive = new User(); inactive.setStatus(UserStatus.LOCKED);
        when(users.findById(1L)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.getReceipt(1L, "REF")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(ledger);
    }
}
