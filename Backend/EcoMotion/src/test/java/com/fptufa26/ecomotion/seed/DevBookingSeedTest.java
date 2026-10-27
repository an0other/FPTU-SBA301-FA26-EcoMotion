package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
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
class DevBookingSeedTest {
    @Autowired BookingRepository bookingRepository;
    @Autowired EntityManager entityManager;

    @Test
    void doesNotSeedManualApprovalBookingsOrTheirDependentRows() {
        assertThat(bookingRepository.findAll()).isNotEmpty()
                .extracting(Booking::getStatus)
                .doesNotContain(BookingStatus.PENDING, BookingStatus.ACCEPTED, BookingStatus.REJECTED);

        var removedCodes = java.util.List.of(
                "EM-2026-9001", "EM-2026-9002", "EM-SEED-R14-001",
                "BK-8F3K29", "BK-7N2W90", "BK-3D9F12", "BK-4M1P88", "BK-4A2K91",
                "BK-4B3L92", "BK-4C4M93", "BK-2R5Y78", "BK-B0A111", "BK-B0A222");
        assertThat(bookingRepository.findAll()).extracting(Booking::getBookingCode)
                .doesNotContainAnyElementsOf(removedCodes);

        for (String entity : java.util.List.of(
                "BookingPolicySnapshot", "BookingCharge", "VehicleSchedule", "PaymentTransaction")) {
            long removedDependents = entityManager.createQuery(
                            "select count(e) from " + entity + " e where e.booking.bookingCode in :codes", Long.class)
                    .setParameter("codes", removedCodes)
                    .getSingleResult();
            assertThat(removedDependents).as("%s rows for removed bookings", entity).isZero();
        }
    }

    @Test
    void keepsPaidAndCompletedBookingsAndTheirSeedData() {
        assertThat(bookingRepository.findByBookingCode("EM-SEED-R12-001")).hasValueSatisfying(
                booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED));
        assertThat(bookingRepository.findByBookingCode("EM-SEED-R16-001")).hasValueSatisfying(
                booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED));
    }
}
