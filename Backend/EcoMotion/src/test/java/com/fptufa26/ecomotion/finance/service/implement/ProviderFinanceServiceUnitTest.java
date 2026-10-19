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
import com.fptufa26.ecomotion.finance.dto.request.UpdateBankAccountRequest;
import com.fptufa26.ecomotion.finance.dto.response.ProviderBankAccountResponse;
import com.fptufa26.ecomotion.finance.dto.response.ProviderPayoutDetailResponse;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountHolderNameException;
import com.fptufa26.ecomotion.finance.exception.InvalidAccountNumberException;
import com.fptufa26.ecomotion.finance.exception.InvalidBankCodeException;
import com.fptufa26.ecomotion.finance.exception.PayoutIneligibleException;
import com.fptufa26.ecomotion.finance.exception.PayoutNotFoundException;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
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
    private ProviderRepository providerRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingChargeRepository bookingChargeRepository;

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
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

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

        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

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
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("UNKNOWN_CODE", "0123456789", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidBankCodeException.class)
                .hasMessageContaining("UNKNOWN_CODE");

        verify(providerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBankAccount rejects masked account number containing asterisk")
    void updateBankAccountRejectsMaskedInput() {
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "******6789", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountNumberException.class)
                .hasMessageContaining("Không được sử dụng số tài khoản đã che");

        verify(providerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBankAccount rejects invalid length account number for bank")
    void updateBankAccountRejectsInvalidLength() {
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "12345", "NGUYEN VAN A");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountNumberException.class);

        verify(providerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBankAccount rejects invalid characters in account holder name")
    void updateBankAccountRejectsInvalidHolderName() {
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "0123456789", "NGUYEN @#$ 123");

        assertThatThrownBy(() -> providerFinanceService.updateBankAccount(10L, request))
                .isInstanceOf(InvalidAccountHolderNameException.class);

        verify(providerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBankAccount successfully saves bank account and preserves leading zeros")
    void updateBankAccountPreservesLeadingZerosAndSaves() {
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateBankAccountRequest request = new UpdateBankAccountRequest("VCB", "001234567890", "nguyen van a");

        ProviderBankAccountResponse response = providerFinanceService.updateBankAccount(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.bankCode()).isEqualTo("VCB");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.accountNumberMasked()).isEqualTo("********7890");
        assertThat(response.accountHolderName()).isEqualTo("NGUYEN VAN A");
        assertThat(sampleProvider.getBankAccountNo()).isEqualTo("001234567890");
    }

    @Test
    @DisplayName("resolveProvider throws AccessDeniedException when provider profile does not exist")
    void throwsAccessDeniedWhenNoProfile() {
        when(providerRepository.findByUser_Id(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> providerFinanceService.getBankAccount(999L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("createMockPayout rejects uncompleted booking")
    void createMockPayoutRejectsUncompletedBooking() {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setStatus(BookingStatus.IN_PROGRESS);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> providerFinanceService.createMockPayout(100L))
                .isInstanceOf(PayoutIneligibleException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("createMockPayout rejects unpaid customer booking")
    void createMockPayoutRejectsUnpaidBooking() {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(paymentTransactionRepository.findByBooking_Id(100L)).thenReturn(List.of());

        assertThatThrownBy(() -> providerFinanceService.createMockPayout(100L))
                .isInstanceOf(PayoutIneligibleException.class)
                .hasMessageContaining("thanh toán");
    }

    @Test
    @DisplayName("createMockPayout rejects provider with missing receiving bank account")
    void createMockPayoutRejectsMissingProviderBank() {
        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(sampleProvider);

        Booking booking = new Booking();
        booking.setId(100L);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setVehicle(vehicle);

        PaymentTransaction customerPayment = new PaymentTransaction();
        customerPayment.setType(PaymentType.DEPOSIT_PAY);
        customerPayment.setStatus(PaymentStatus.PAID);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(paymentTransactionRepository.findByBooking_Id(100L)).thenReturn(List.of(customerPayment));

        assertThatThrownBy(() -> providerFinanceService.createMockPayout(100L))
                .isInstanceOf(PayoutIneligibleException.class)
                .hasMessageContaining("ngân hàng");
    }

    @Test
    @DisplayName("createMockPayout calculates netAmount = grossAmount - platformFee and takes destination snapshot")
    void createMockPayoutCalculatesCorrectlyAndSnapshots() {
        sampleProvider.setBankCode("VCB");
        sampleProvider.setBankName("Vietcombank");
        sampleProvider.setBankAccountNo("0123456789");
        sampleProvider.setBankAccountHolder("NGUYEN VAN A");
        sampleProvider.setCommissionRate(new BigDecimal("0.1000"));

        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("VinFast");
        vehicle.setModel("VF 8");
        vehicle.setLicensePlate("51A-12345");
        vehicle.setProvider(sampleProvider);

        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("BK-TEST-100");
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setVehicle(vehicle);
        booking.setTotalAmount(new BigDecimal("1000000.00"));

        PaymentTransaction customerPayment = new PaymentTransaction();
        customerPayment.setType(PaymentType.DEPOSIT_PAY);
        customerPayment.setStatus(PaymentStatus.PAID);

        BookingCharge baseRental = new BookingCharge();
        baseRental.setChargeType(ChargeType.BASE_RENTAL);
        baseRental.setAmount(new BigDecimal("1000000.00"));
        baseRental.setWaived(false);

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(paymentTransactionRepository.findByBooking_Id(100L)).thenReturn(List.of(customerPayment));
        when(paymentTransactionRepository.existsByBooking_IdAndType(100L, PaymentType.PAYOUT)).thenReturn(false);
        when(bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(100L)).thenReturn(List.of(baseRental));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransaction payout = providerFinanceService.createMockPayout(100L);

        assertThat(payout).isNotNull();
        assertThat(payout.getType()).isEqualTo(PaymentType.PAYOUT);
        assertThat(payout.getDirection()).isEqualTo(PaymentDirection.OUT);
        assertThat(payout.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payout.getAmount()).isEqualByComparingTo("900000.00");
        assertThat(payout.getGatewayRef()).isEqualTo("MOCK-PAYOUT-BK-TEST-100");
        assertThat(payout.getBankCodeSnapshot()).isEqualTo("VCB");
        assertThat(payout.getBankNameSnapshot()).isEqualTo("Vietcombank");
        assertThat(payout.getBankAccountNoSnapshot()).isEqualTo("******6789");
        assertThat(payout.getBankAccountHolderSnapshot()).isEqualTo("NGUYEN VAN A");
    }

    @Test
    @DisplayName("createMockPayout is idempotent and does not recreate payout on repeated call")
    void createMockPayoutIsIdempotent() {
        sampleProvider.setBankCode("VCB");
        sampleProvider.setBankName("Vietcombank");
        sampleProvider.setBankAccountNo("0123456789");
        sampleProvider.setBankAccountHolder("NGUYEN VAN A");

        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(sampleProvider);

        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("BK-TEST-100");
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setVehicle(vehicle);

        PaymentTransaction customerPayment = new PaymentTransaction();
        customerPayment.setType(PaymentType.DEPOSIT_PAY);
        customerPayment.setStatus(PaymentStatus.PAID);

        PaymentTransaction existingPayout = new PaymentTransaction();
        existingPayout.setId(777L);
        existingPayout.setType(PaymentType.PAYOUT);
        existingPayout.setAmount(new BigDecimal("900000.00"));

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(paymentTransactionRepository.findByBooking_Id(100L)).thenReturn(List.of(customerPayment));
        when(paymentTransactionRepository.existsByBooking_IdAndType(100L, PaymentType.PAYOUT)).thenReturn(true);
        when(paymentTransactionRepository.findByBooking_IdAndType(100L, PaymentType.PAYOUT)).thenReturn(Optional.of(existingPayout));

        PaymentTransaction result = providerFinanceService.createMockPayout(100L);

        assertThat(result.getId()).isEqualTo(777L);
        verify(paymentTransactionRepository, never()).save(any());
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

        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));
        when(paymentTransactionRepository.findByIdAndBooking_Vehicle_Provider_Id(50L, 1L)).thenReturn(Optional.of(pt));

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
        when(providerRepository.findByUser_Id(10L)).thenReturn(Optional.of(sampleProvider));
        when(paymentTransactionRepository.findByIdAndBooking_Vehicle_Provider_Id(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> providerFinanceService.getPayoutDetail(10L, 999L))
                .isInstanceOf(PayoutNotFoundException.class)
                .hasMessageContaining("999");
    }
}
