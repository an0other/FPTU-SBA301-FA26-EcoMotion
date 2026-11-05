package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.enums.BookingPaymentStatus;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.repository.TripEvidenceRepository;
import com.fptufa26.ecomotion.booking.service.BookingPaymentStateService;
import com.fptufa26.ecomotion.finance.enums.PaymentEvidenceStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RenterCompletedLifecycleSeedIntegrationTest {

    @Autowired
    RenterCompletedLifecycleSeeder seeder;
    @Autowired
    BookingRepository bookings;
    @Autowired
    PaymentTransactionRepository ledger;
    @Autowired
    PaymentRequestRepository paymentRequests;
    @Autowired
    TripEvidenceRepository tripEvidence;
    @Autowired
    BookingPaymentStateService paymentStates;

    @Test
    void demoCompleteBookingIsFullyPaidWithEvidenceChain() {
        seeder.seed();

        var booking = bookings.findByBookingCode(RenterCompletedLifecycleSeeder.BOOKING_CODE).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getDriverConfirmedAt()).isNotNull();
        assertThat(booking.getActualStartAt()).isNotNull();
        assertThat(booking.getActualEndAt()).isNotNull();
        assertThat(booking.getRenterEndConfirmedAt()).isNotNull();

        var depositRequest = paymentRequests
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT)
                .orElseThrow();
        assertThat(depositRequest.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(depositRequest.getFirstProofAt()).isNotNull();
        assertThat(depositRequest.getEvidences()).isNotEmpty();
        assertThat(depositRequest.getEvidences()).allMatch(e -> e.getStatus() == PaymentEvidenceStatus.ACCEPTED);

        var finalRequest = paymentRequests
                .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                .orElseThrow();
        assertThat(finalRequest.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(finalRequest.getEvidences()).isNotEmpty();

        var txs = ledger.findByBooking_Id(booking.getId());
        assertThat(txs).anyMatch(t -> t.getType() == PaymentType.DEPOSIT_PAY && t.getPaymentRequest() != null);
        assertThat(txs).anyMatch(t -> t.getType() == PaymentType.SETTLEMENT && t.getPaymentRequest() != null);

        assertThat(tripEvidence.findByBooking_IdOrderByRecordedAtAsc(booking.getId())).hasSizeGreaterThanOrEqualTo(5);

        var state = paymentStates.evaluate(booking, txs);
        assertThat(state.status()).isEqualTo(BookingPaymentStatus.FULLY_PAID);
        assertThat(state.remaining()).isEqualByComparingTo("0");
    }

    @Test
    void reseedIsIdempotent() {
        seeder.seed();
        seeder.seed();
        assertThat(bookings.findByBookingCode(RenterCompletedLifecycleSeeder.BOOKING_CODE)).isPresent();
    }
}
