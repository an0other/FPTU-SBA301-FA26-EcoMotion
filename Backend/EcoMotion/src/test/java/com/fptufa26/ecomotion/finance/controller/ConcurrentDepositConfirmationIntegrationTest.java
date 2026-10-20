package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.dto.request.ConfirmPaymentRequest;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.repository.ProviderBankAccountRepository;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ConcurrentDepositConfirmationIntegrationTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentRequestService paymentRequestService;
    @Autowired ProviderBankAccountRepository bankAccountRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired VehicleScheduleRepository scheduleRepository;
    @Autowired PaymentTransactionRepository transactionRepository;
    @Autowired PaymentRequestRepository paymentRequestRepository;

    @Test
    @Transactional
    void onlyOneCompetingDepositCanLockTheSameVehicleSchedule() throws Exception {
        Booking first = bookingRepository.findByBookingCode("BK-4M1P88").orElseThrow();
        Booking second = bookingRepository.findByBookingCode("BK-2R5Y78").orElseThrow();
        second.setVehicle(first.getVehicle());
        second.setPlan(first.getPlan());
        second.setPickupTime(first.getPickupTime());
        second.setTotalAmount(first.getTotalAmount());
        first.setStatus(BookingStatus.ACCEPTED);
        second.setStatus(BookingStatus.ACCEPTED);
        bookingRepository.saveAllAndFlush(List.of(first, second));
        ensureBankAccount(first);

        PaymentRequest firstRequest = paymentRequestService.createDepositPaymentRequest(first);
        PaymentRequest secondRequest = paymentRequestService.createDepositPaymentRequest(second);
        firstRequest.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        secondRequest.setStatus(PaymentRequestStatus.PROOF_SUBMITTED);
        paymentRequestRepository.saveAllAndFlush(List.of(firstRequest, secondRequest));
        Long providerUserId = first.getVehicle().getProvider().getUser().getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> one = executor.submit(() -> confirm(start, firstRequest, providerUserId, "RACE-ONE"));
            Future<Boolean> two = executor.submit(() -> confirm(start, secondRequest, providerUserId, "RACE-TWO"));
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        Booking firstResult = bookingRepository.findById(first.getId()).orElseThrow();
        Booking secondResult = bookingRepository.findById(second.getId()).orElseThrow();
        assertThat(List.of(firstResult.getStatus(), secondResult.getStatus()))
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.ACCEPTED);
        long schedules = scheduleRepository.findByBooking_Id(first.getId()).size()
                + scheduleRepository.findByBooking_Id(second.getId()).size();
        long transactions = transactionRepository.findByBooking_Id(first.getId()).size()
                + transactionRepository.findByBooking_Id(second.getId()).size();
        assertThat(schedules).isEqualTo(1);
        assertThat(transactions).isEqualTo(1);
    }

    private boolean confirm(CountDownLatch start, PaymentRequest request, Long providerUserId, String reference)
            throws InterruptedException {
        start.await();
        try {
            paymentRequestService.confirmPayment(request.getId(), providerUserId,
                    new ConfirmPaymentRequest(request.getAmount(), reference, null));
            return true;
        } catch (ConflictException expected) {
            return false;
        }
    }

    private void ensureBankAccount(Booking booking) {
        Long providerId = booking.getVehicle().getProvider().getId();
        if (bankAccountRepository.findByProvider_IdAndStatus(providerId, BankAccountStatus.ACTIVE).isPresent()) return;
        ProviderBankAccount account = new ProviderBankAccount();
        account.setProvider(providerRepository.findById(providerId).orElseThrow());
        account.setBankCode("VCB");
        account.setBankName("Vietcombank");
        account.setAccountNumber("0123456789");
        account.setAccountHolderName("ECOMOTION RACE TEST");
        bankAccountRepository.saveAndFlush(account);
    }
}
