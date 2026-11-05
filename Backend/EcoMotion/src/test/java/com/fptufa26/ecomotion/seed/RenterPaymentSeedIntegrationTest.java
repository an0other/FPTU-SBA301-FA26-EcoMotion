package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RenterPaymentSeedIntegrationTest {
    @Autowired RenterPaymentDataSeeder seeder;
    @Autowired BookingRepository bookings;
    @Autowired PaymentTransactionRepository ledger;
    @Autowired PaymentRequestRepository requests;
    @Autowired EntityManager em;

    @Test void newSeedHasConfirmedDepositAndExistingCancelledSeedIsBackfilledWithoutNewMoney() {
        var b=bookings.findByBookingCode("EM-SEED-R12-001").orElseThrow();
        var request=requests.findByBooking_IdAndPaymentType(b.getId(),PaymentRequestType.DEPOSIT).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.CONFIRMED);
        assertThat(request.getPaidAmount()).isEqualByComparingTo("330000");
        assertThat(request.getExpiresAt()).isNull();
        var original=ledger.findByBooking_Id(b.getId());
        assertThat(original).allMatch(t -> t.getPaymentRequest().getId().equals(request.getId()));
        original.forEach(t -> {
            t.setPaymentRequest(null);
            t.setAppliedAmount(java.math.BigDecimal.ZERO);
        });
        requests.delete(request);
        em.flush();
        b.setStatus(BookingStatus.CANCELLED);b.setCancelledAt(java.time.Instant.now());b.setCancelReason(CancelReason.RENTER_REQUEST);
        seeder.seed();seeder.seed();em.flush();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        var repaired=requests.findByBooking_IdAndPaymentType(b.getId(),PaymentRequestType.DEPOSIT).orElseThrow();
        assertThat(repaired.getPaidAmount()).isEqualByComparingTo("330000");
        assertThat(ledger.findByBooking_Id(b.getId())).hasSize(original.size());
        assertThat(ledger.findByBooking_Id(b.getId())).allMatch(t -> t.getPaymentRequest().getId().equals(repaired.getId()));
        assertThat(ledger.findByBooking_Id(b.getId())).allMatch(t -> t.getAppliedAmount().compareTo(repaired.getPaidAmount()) == 0);
    }
}
