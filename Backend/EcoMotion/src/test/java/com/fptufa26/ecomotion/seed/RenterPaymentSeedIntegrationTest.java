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
    @Autowired com.fptufa26.ecomotion.dispute.repository.DisputeRepository disputes;

    @Test void confirmedDemoBookingDoesNotStartWithAnUnsubmittedComplaint() {
        var booking = bookings.findByBookingCode("BK-ECO-001").orElseThrow();
        assertThat(disputes.findByBooking_Id(booking.getId())).isEmpty();
    }


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

    @Test void occupiedSeedDateUsesNextAvailableDayWithoutChangingExistingBooking() {
        var existing = bookings.findByBookingCode("EM-SEED-R12-001").orElseThrow();
        var originalPickup = existing.getPickupTime();
        existing.setBookingCode("R12-EXISTING-COLLISION");
        requests.findByBooking_IdAndPaymentType(existing.getId(), PaymentRequestType.DEPOSIT).orElseThrow()
                .setTransferReference("R12EXISTINGDEP");
        ledger.findByBooking_Id(existing.getId()).forEach(t -> t.setGatewayRef("R12-EXISTING-DEPOSIT"));
        em.flush();
        seeder.seed();
        em.flush();
        var created = bookings.findByBookingCode("EM-SEED-R12-001").orElseThrow();
        assertThat(created.getPickupTime()).isAfter(originalPickup);
        assertThat(existing.getPickupTime()).isEqualTo(originalPickup);
        assertThat(created.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        seeder.seed();
        assertThat(ledger.findByBooking_Id(created.getId())).hasSize(1);
    }

    @Test void fullyOccupiedSearchWindowSkipsDemoWithoutWritingMoney() {
        var existing = bookings.findByBookingCode("EM-SEED-R12-001").orElseThrow();
        existing.setBookingCode("R12-FULLY-OCCUPIED");
        em.createQuery("update VehicleSchedule s set s.endAt=:end where s.booking.id=:id")
                .setParameter("end", java.time.Instant.now().plus(400, java.time.temporal.ChronoUnit.DAYS))
                .setParameter("id", existing.getId()).executeUpdate();
        em.flush();
        long transactionCount = ledger.count();
        seeder.seed();
        assertThat(bookings.existsByBookingCode("EM-SEED-R12-001")).isFalse();
        assertThat(ledger.count()).isEqualTo(transactionCount);
    }
}
