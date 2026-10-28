package com.fptufa26.ecomotion.schedule.service;

import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.ProviderType;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduleAvailabilityConsistencyTest {
    @Autowired EntityManager entityManager;
    @Autowired ScheduleService scheduleService;
    @Autowired VehicleScheduleRepository repository;

    private Vehicle vehicle;
    private final Instant from = Instant.parse("2026-10-26T19:00:00Z");
    private final Instant to = Instant.parse("2026-10-29T19:00:00Z");

    @BeforeEach
    void createVehicle() {
        String key = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail(key + "@availability.test");
        user.setPasswordHash("test-password");
        user.setRole(Role.PROVIDER);
        entityManager.persist(user);

        Provider provider = new Provider();
        provider.setUser(user);
        provider.setProviderType(ProviderType.INDIVIDUAL);
        provider.setDisplayName("Availability test provider");
        provider.setHotline("0901234567");
        provider.setDepotAddress("Test depot");
        provider.setCommissionRate(new BigDecimal("0.1"));
        provider.setDepositRatio(new BigDecimal("0.3"));
        entityManager.persist(provider);

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
        entityManager.persist(vehicle);
    }

    @Test
    void activeScheduleIsReturnedEvenWithAnExpiredExpiresAt() {
        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setType(ScheduleType.BOOKED);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setStartAt(from.plusSeconds(3600));
        schedule.setEndAt(to.minusSeconds(3600));
        schedule.setExpiresAt(Instant.parse("2000-01-01T00:00:00Z"));
        entityManager.persist(schedule);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsActiveOverlap(vehicle.getId(), from, to)).isTrue();
        assertThat(repository.findOccupiedInRange(vehicle.getId(), from, to))
                .extracting(VehicleSchedule::getId).containsExactly(schedule.getId());
        assertThat(scheduleService.isRangeFree(vehicle.getId(), from, to)).isFalse();
        assertThat(scheduleService.occupiedWindows(vehicle.getId(), from, to)).hasSize(1);
    }

    @Test
    void scheduleCreatedByBookIsVisibleToBothQueries() {
        scheduleService.book(vehicle, null, from, to);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.existsActiveOverlap(vehicle.getId(), from, to)).isTrue();
        assertThat(repository.findOccupiedInRange(vehicle.getId(), from, to)).hasSize(1);
        assertThat(scheduleService.occupiedWindows(vehicle.getId(), from, to)).hasSize(1);
        assertThat(scheduleService.isRangeFree(vehicle.getId(), from, to)).isFalse();

        assertThat(repository.existsActiveOverlap(vehicle.getId(), to, to.plusSeconds(3600))).isFalse();
        assertThat(repository.findOccupiedInRange(vehicle.getId(), to, to.plusSeconds(3600))).isEmpty();
    }
}
