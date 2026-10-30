package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.finance.dto.request.UpdateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.response.ProviderBankAccountResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutDetailResponse;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountHolderNameException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountNumberException;
import com.fptufa26.ecomotion.finance.exception.InvalidBankCodeException;
import com.fptufa26.ecomotion.finance.exception.PayoutIneligibleException;
import com.fptufa26.ecomotion.finance.exception.PayoutNotFoundException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderFinanceServiceUnitTest {

    @Mock
    private UserService userService;

    @Mock
    private ProviderBankAccountRepository providerBankAccountRepository;

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private BookingPaymentLedgerService bookingPaymentLedgerService;

    @Mock
    private org.springframework.core.env.Environment environment;

    @InjectMocks
    private ProviderFinanceServiceImpl providerFinanceService;

    private Provider sampleProvider;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setEmail("provider@test.vn");

        sampleProvider = new Provider();
        sampleProvider.setId(1L);
        sampleProvider.setUser(sampleUser);
        sampleProvider.setProviderType(ProviderType.BUSINESS);
        sampleProvider.setDisplayName("Test Fleet");
        sampleProvider.setHotline("0901234567");
        sampleProvider.setDepotAddress("123 Test St");
        sampleProvider.setCommissionRate(new BigDecimal("0.1000"));
        sampleProvider.setDepositRatio(new BigDecimal("0.3000"));
        sampleProvider.setStatus(ProviderStatus.APPROVED);
    }

    @Test
    @DisplayName("getBankAccount returns null when bank account is not set")
    void getBankAccountReturnsNullWhenEmpty() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        ProviderBankAccountResponse response = providerFinanceService.getBankAccount(10L);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("getBankAccount returns masked account number and bank code")
    void getBankAccountReturnsMaskedData() {
        sampleProvider.setBankCode("VCB");
        sampleProvider.setBankName("Vietcombank");
        sampleProvider.setBankAccountNo("0123456789");
        sampleProvider.setBankAccountHolder("NGUYEN VAN A");
        sampleProvider.setUpdatedAt(Instant.now());

        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        ProviderBankAccountResponse response = providerFinanceService.getBankAccount(10L);

        assertThat(response).isNotNull();
        assertThat(response.bankCode()).isEqualTo("VCB");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.accountNumberMasked()).isEqualTo("******6789");
        assertThat(response.accountHolderName()).isEqualTo("NGUYEN VAN A");
        assertThat(response.verified()).isFalse();
    }

    @Test
    @DisplayName("updateBankAccount rejects unknown bank code with InvalidBankCodeException")
    void updateBankAccountRejectsUnknownBankCode() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("UNKNOWN_CODE", "0123456789", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidBankCodeException.class)
                .hasMessageContaining("UNKNOWN_CODE");

        verify(userService, never()).updateProviderBankSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateBankAccount rejects masked account number containing asterisk")
    void updateBankAccountRejectsMaskedInput() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "******6789", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Không được sử dụng số tài khoản đã che");

        verify(userService, never()).updateProviderBankSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateBankAccount rejects invalid length account number for bank")
    void updateBankAccountRejectsInvalidLength() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "12345", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountNumberException.class);

        verify(userService, never()).updateProviderBankSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateBankAccount rejects invalid characters in account holder name")
    void updateBankAccountRejectsInvalidHolderName() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "0123456789", "NGUYEN @#$ 123");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountHolderNameException.class);

        verify(userService, never()).updateProviderBankSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateBankAccount successfully saves bank account and preserves leading zeros")
    void updateBankAccountPreservesLeadingZerosAndSaves() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));
        when(providerBankAccountRepository.save(any(com.fptufa26.ecomotion.finance.entity.ProviderBankAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "001234567890", "nguyen van a");

        ProviderBankAccountResponse response = providerFinanceService.updateBankAccount(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.bankCode()).isEqualTo("VCB");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.accountNumberMasked()).isEqualTo("********7890");
        assertThat(response.accountHolderName()).isEqualTo("NGUYEN VAN A");
        verify(userService).updateProviderBankSnapshot(
                1L, "VCB", "Vietcombank", "001234567890", "NGUYEN VAN A");
    }

    @Test
    @DisplayName("resolveProvider throws AccessDeniedException when provider profile does not exist")
    void throwsAccessDeniedWhenNoProfile() {
        when(userService.findProviderByUserId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> providerFinanceService.getBankAccount(999L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legacyPayoutCannotInventPaidMoneyWithoutAnAuthenticatedAdmin() {
        assertThatThrownBy(() -> providerFinanceService.createMockPayout(100L))
                .isInstanceOf(PayoutIneligibleException.class)
                .hasMessageContaining("Admin");
        verify(bookingPaymentLedgerService, never()).saveTransaction(any());
    }

    @Test void productionProfileBlocksSandboxEvenWhenFlagIsEnabled() {
        User admin = new User(); admin.setRole(com.fptufa26.ecomotion.user.enums.Role.ADMIN);
        admin.setStatus(com.fptufa26.ecomotion.user.enums.UserStatus.ACTIVE);
        when(userService.findById(12L)).thenReturn(Optional.of(admin));
        when(environment.getProperty("app.finance.sandbox-payout-enabled", Boolean.class, false)).thenReturn(true);
        when(environment.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(true);
        assertThatThrownBy(() -> providerFinanceService.createSandboxPayout(12L, 100L)).isInstanceOf(PayoutIneligibleException.class);
        verify(bookingPaymentLedgerService, never()).saveTransaction(any());
    }

    @Test void sandboxPayoutIsDisabledByDefault() {
        User admin = new User(); admin.setRole(com.fptufa26.ecomotion.user.enums.Role.ADMIN);
        admin.setStatus(com.fptufa26.ecomotion.user.enums.UserStatus.ACTIVE);
        when(userService.findById(12L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> providerFinanceService.createSandboxPayout(12L, 100L)).isInstanceOf(PayoutIneligibleException.class);
        verify(bookingPaymentLedgerService, never()).saveTransaction(any());
    }

    @Test
    @DisplayName("Historical payout snapshot is immutable when provider changes bank account later")
    void snapshotRemainsImmutableAfterBankUpdate() {
        PaymentTransaction pt = new PaymentTransaction();
        pt.setId(50L);
        pt.setType(PaymentType.PAYOUT);
        pt.setAmount(new BigDecimal("900000.00"));
        pt.setStatus(PaymentStatus.PAID);
        pt.setBankCodeSnapshot("VCB");
        pt.setBankNameSnapshot("Vietcombank");
        pt.setBankAccountNoSnapshot("******6789");
        pt.setBankAccountHolderSnapshot("NGUYEN VAN A");

        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF 8");
        vehicle.setLicensePlate("51A-12345");
        vehicle.setProvider(sampleProvider);

        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("BK-TEST-100");
        booking.setVehicle(vehicle);
        booking.setTotalAmount(new BigDecimal("1000000.00"));

        pt.setBooking(booking);

        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));
        when(bookingPaymentLedgerService.findProviderTransaction(50L, 1L)).thenReturn(Optional.of(pt));

        ProviderPayoutDetailResponse detail1 = providerFinanceService.getPayoutDetail(10L, 50L);
        assertThat(detail1.destinationBank().bankCode()).isEqualTo("VCB");
        assertThat(detail1.destinationBank().accountNumberMasked()).isEqualTo("******6789");

        sampleProvider.setBankCode("TCB");
        sampleProvider.setBankName("Techcombank");
        sampleProvider.setBankAccountNo("9876543210");
        sampleProvider.setBankAccountHolder("TRAN VAN B");

        ProviderPayoutDetailResponse detail2 = providerFinanceService.getPayoutDetail(10L, 50L);
        assertThat(detail2.destinationBank().bankCode()).isEqualTo("VCB");
        assertThat(detail2.destinationBank().accountNumberMasked()).isEqualTo("******6789");
        assertThat(detail2.destinationBank().accountHolderName()).isEqualTo("NGUYEN VAN A");
    }

    @Test
    @DisplayName("getPayoutDetail throws PayoutNotFoundException for non-existent payout or unauthorized access")
    void getPayoutDetailThrowsNotFound() {
        when(userService.findProviderByUserId(10L)).thenReturn(Optional.of(sampleProvider));
        when(bookingPaymentLedgerService.findProviderTransaction(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> providerFinanceService.getPayoutDetail(10L, 999L))
                .isInstanceOf(PayoutNotFoundException.class)
                .hasMessageContaining("999");
    }
}
