package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduleCreationIntegrationTest {

    @Autowired ProviderScheduleService providerScheduleService;
    @Autowired ScheduleService scheduleService;
    @Autowired VehicleScheduleRepository scheduleRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private Vehicle vehicle;
    private Long userId;
    private final Instant base = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            User user = new User();
            user.setEmail(key + "@schedule.test");
            user.setPasswordHash("test-password-hash");
            user.setRole(Role.PROVIDER);
            userRepository.save(user);
            userId = user.getId();

            Provider provider = new Provider();
            provider.setUser(user);
            provider.setProviderType(ProviderType.INDIVIDUAL);
            provider.setDisplayName("Schedule test provider");
            provider.setHotline("0901234567");
            provider.setDepotAddress("Test depot");
            provider.setCommissionRate(new BigDecimal("0.1"));
            provider.setDepositRatio(new BigDecimal("0.3"));
            provider = providerRepository.save(provider);

            vehicle = new Vehicle();
            vehicle.setProvider(provider);
            vehicle.setLicensePlate(key.substring(0, 20));
            vehicle.setChassisNo(key);
            vehicle.setEngineNo(key);
            vehicle.setBrand("VinFast");
            vehicle.setModel("VF 6");
            vehicle.setManufactureYear(2026);
            vehicle.setSeatCount(5);
            vehicle.setTransmission(Transmission.AUTOMATIC);
            vehicle.setColor("White");
            vehicle.setFuelType(FuelType.EV);
            vehicle.setMaintenanceBufferMinutes(0);
            vehicleRepository.save(vehicle);
        });
    }

    @ParameterizedTest
    @CsvSource({"8,12", "18,22", "12,18", "8,22", "10,20"})
    void rejectsEveryOverlapShape(int startHour, int endHour) {
        providerScheduleService.createBlockSchedule(userId, request(10, 20));

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request(startHour, endHour)))
                .isInstanceOf(ConflictException.class);
        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(ScheduleType.class)
    void rejectsRangeCrossingSchedulesOnBothSides(ScheduleType type) {
        saveSchedule(type, ScheduleStatus.ACTIVE, 0, 2);
        saveSchedule(type, ScheduleStatus.ACTIVE, 4, 6);

        assertThatThrownBy(() -> providerScheduleService.createBlockSchedule(userId, request(1, 5)))
                .isInstanceOf(ConflictException.class);
        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).hasSize(2);
    }

    @Test
    void allowsGapAndTouchingEndpoints() {
        providerScheduleService.createBlockSchedule(userId, request(0, 2));
        providerScheduleService.createBlockSchedule(userId, request(4, 6));
        providerScheduleService.createBlockSchedule(userId, request(2, 4));

        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).hasSize(3);
    }

    @Test
    void ignoresCancelledSchedules() {
        saveSchedule(ScheduleType.BLOCKED, ScheduleStatus.CANCELLED, 0, 6);

        providerScheduleService.createBlockSchedule(userId, request(1, 5));

        assertThat(scheduleRepository.findByVehicle_IdAndStatus(vehicle.getId(), ScheduleStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void bookRejectsEmptyOrReversedRange() {
        assertThatThrownBy(() -> scheduleService.book(vehicle, null, at(2), at(2)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> scheduleService.book(vehicle, null, at(2), at(1)))
                .isInstanceOf(BadRequestException.class);
        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).isEmpty();
    }

    @Test
    void bookRejectsOverlapEvenWithoutAvailabilityPrecheck() {
        providerScheduleService.createBlockSchedule(userId, request(0, 4));

        assertThatThrownBy(() -> scheduleService.book(vehicle, null, at(2), at(6)))
                .isInstanceOf(ConflictException.class);
        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).hasSize(1);
    }

    @Test
    void onlyOneConcurrentBlockCanCreateTheFirstSchedule() throws Exception {
        assertOnlyOneSucceeds(
                () -> providerScheduleService.createBlockSchedule(userId, request(0, 4)),
                () -> providerScheduleService.createBlockSchedule(userId, request(2, 6)));
    }

    @Test
    void blockAndBookingShareTheSameVehicleLock() throws Exception {
        assertOnlyOneSucceeds(
                () -> providerScheduleService.createBlockSchedule(userId, request(0, 4)),
                () -> {
                    scheduleService.book(vehicle, null, at(2), at(6));
                    return null;
                });
    }

    @Test
    void onlyOneConcurrentBookingCanCreateTheFirstSchedule() throws Exception {
        assertOnlyOneSucceeds(
                () -> {
                    scheduleService.book(vehicle, null, at(0), at(4));
                    return null;
                },
                () -> {
                    scheduleService.book(vehicle, null, at(2), at(6));
                    return null;
                });
    }

    private void assertOnlyOneSucceeds(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> createConcurrently(ready, start, first));
            var two = executor.submit(() -> createConcurrently(ready, start, second));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(scheduleRepository.findByVehicle_IdOrderByStartAtAsc(vehicle.getId())).hasSize(1);
    }

    private boolean createConcurrently(CountDownLatch ready, CountDownLatch start, Callable<?> create) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent schedule creation");
        }
        try {
            create.call();
            return true;
        } catch (ConflictException expected) {
            return false;
        }
    }

    private void saveSchedule(ScheduleType type, ScheduleStatus status, int startHour, int endHour) {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setType(type);
        schedule.setStatus(status);
        schedule.setStartAt(at(startHour));
        schedule.setEndAt(at(endHour));
        scheduleRepository.saveAndFlush(schedule);
    }

    private CreateBlockScheduleRequest request(int startHour, int endHour) {
        return new CreateBlockScheduleRequest(vehicle.getId(), ScheduleType.BLOCKED,
                at(startHour), at(endHour), "Test block");
    }

    private Instant at(int hour) {
        return base.plus(hour, ChronoUnit.HOURS);
    }
}
