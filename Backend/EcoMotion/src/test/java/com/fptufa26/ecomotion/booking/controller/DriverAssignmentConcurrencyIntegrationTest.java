package com.fptufa26.ecomotion.booking.controller;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class DriverAssignmentConcurrencyIntegrationTest {
    @Autowired BookingRepository bookings;
    @Autowired DriverRepository drivers;
    @Autowired DriverAssignmentRepository assignments;
    @Autowired TripIncidentRepository incidents;
    @Autowired DriverOptimizationService optimization;
    @Autowired PlatformTransactionManager transactions;
    @Test @DirtiesContext
    void twoConcurrentAssignmentsCannotReserveOverlappingTrips() throws Exception {
        var tx = new TransactionTemplate(transactions);
        long[] ids = tx.execute(state -> {
            var driver = drivers.findAll().stream().filter(d -> d.getStatus() == DriverStatus.APPROVED)
                    .filter(d -> bookings.findAll().stream().filter(b -> b.getVehicle().getProvider().getId().equals(d.getProvider().getId())).count() >= 2)
                    .findFirst().orElseThrow();
            var all = bookings.findAll();
            all.stream().filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driver.getId())).forEach(b -> b.setDriver(null));
            var targets = all.stream().filter(b -> b.getVehicle().getProvider().getId().equals(driver.getProvider().getId())).limit(2).toList();
            driver.setActivityStatus(DriverActivityStatus.AVAILABLE); driver.setLicenseClass("E");
            driver.setLicenseExpiry(LocalDate.now().plusYears(1)); driver.getUser().setStatus(UserStatus.ACTIVE);
            incidents.findAll().stream().filter(i -> i.getDriver() != null && i.getDriver().getId().equals(driver.getId())).forEach(i -> i.setStatus(TripIncidentStatus.RESOLVED));
            for (Booking booking : targets) {
                assignments.deleteAll(assignments.findByBooking_IdOrderByCreatedAtDesc(booking.getId()));
                booking.setDriver(null); booking.setStatus(BookingStatus.CONFIRMED);
                booking.setPickupTime(Instant.now().plus(Duration.ofDays(30)));
                booking.setPickupAddress(""); booking.setActualStartAt(null); booking.setActualEndAt(null);
            }
            bookings.flush(); drivers.flush(); incidents.flush();
            return new long[]{targets.get(0).getId(), targets.get(1).getId(), driver.getId(), driver.getProvider().getId()};
        });
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                long bookingId = ids[index];
                results.add(executor.submit(() -> {
                    start.await();
                    try { optimization.manualAssign(bookingId, ids[2], ids[3]); return true; }
                    catch (ConflictException expected) { return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            Long assigned = tx.execute(state -> bookings.findAllById(List.of(ids[0], ids[1])).stream().filter(b -> b.getDriver() != null).count());
            assertThat(assigned).isEqualTo(1L);
        } finally { executor.shutdownNow(); }
    }
}
