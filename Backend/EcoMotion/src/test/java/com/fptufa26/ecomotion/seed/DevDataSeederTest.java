package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DevDataSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RentalPlanRepository rentalPlanRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Test
    @DisplayName("the five dev accounts exist with the documented emails, roles and password")
    void seedsDevAccounts() {
        assertSeed("renter01@ecomotion.vn", Role.RENTER);
        assertSeed("provider.a@ecomotion.vn", Role.PROVIDER);
        assertSeed("admin01@ecomotion.vn", Role.ADMIN);
        assertSeed("provider.b@ecomotion.vn", Role.PROVIDER);
        assertSeed("renter.demo@ecomotion.vn", Role.RENTER);
    }

    @Test
    @DisplayName("re-running the seeder does not duplicate accounts")
    void isIdempotent() {
        devDataSeeder.run(null);
        long settled = userRepository.count();

        devDataSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(settled);
    }

    @Test
    @DisplayName("providers occupy the ids images expects and the text decoded cleanly")
    void pinnedIdsAndTextAreIntact() {
        User providerA = userRepository.findById(2L).orElseThrow();
        User providerB = userRepository.findById(4L).orElseThrow();
        assertThat(providerA.getEmail()).isEqualTo("provider.a@ecomotion.vn");
        assertThat(providerA.getRole()).isEqualTo(Role.PROVIDER);
        assertThat(providerB.getEmail()).isEqualTo("provider.b@ecomotion.vn");
        assertThat(providerB.getRole()).isEqualTo(Role.PROVIDER);

        assertThat(providerA.getFullName())
                .isEqualTo("Nguy\u1ec5n V\u0103n A")
                .doesNotContain("\ufffd");
        assertThat(providerB.getFullName()).isEqualTo("Tr\u1ea7n Th\u1ecb B");

        Long providerAId = providerRepository.findByUser_Id(2L).orElseThrow().getId();
        Long providerBId = providerRepository.findByUser_Id(4L).orElseThrow().getId();
        List<Vehicle> byProviderA = vehicleRepository.findByProvider_IdOrderByIdAsc(providerAId);
        List<Vehicle> byProviderB = vehicleRepository.findByProvider_IdOrderByIdAsc(providerBId);
        assertThat(byProviderA)
                .filteredOn(vehicle -> vehicle.getStatus() == VehicleStatus.APPROVED)
                .extracting(Vehicle::getId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(byProviderA)
                .filteredOn(vehicle -> vehicle.getStatus() == VehicleStatus.PENDING)
                .extracting(Vehicle::getId).containsExactly(9L);
        assertThat(byProviderB).extracting(Vehicle::getId).containsExactly(5L, 6L, 7L, 8L);

        assertThat(vehicleRepository.findById(1L).orElseThrow().getColor()).isEqualTo("Tr\u1eafng");
    }

    @Test
    @DisplayName("a newly registered account gets a fresh id instead of colliding with a seeded one")
    void newAccountGetsAnIdPastTheSeededOnes() {
        User fresh = new User();
        fresh.setEmail("fresh-" + System.nanoTime() + "@ecomotion.vn");
        fresh.setPasswordHash(passwordEncoder.encode("Fresh@12345"));
        fresh.setFullName("Fresh Renter");
        fresh.setRole(Role.RENTER);
        fresh.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.saveAndFlush(fresh);

        assertThat(saved.getId()).isGreaterThan(5L);
    }

    @Test
    @DisplayName("the fleet is varied: no two cars share a rating or a price")
    void seededFleetIsVaried() {
        List<Vehicle> fleet = vehicleRepository.findAll().stream()
                .filter(vehicle -> vehicle.getStatus() == VehicleStatus.APPROVED)
                .toList();
        assertThat(fleet).hasSize(8);

        assertThat(fleet)
                .extracting(vehicle -> vehicle.getRatingAvg().doubleValue())
                .doesNotHaveDuplicates();

        assertThat(fleet).filteredOn(vehicle -> vehicle.getRatingCount() == 0).hasSize(1);

        Set<Long> approvedVehicleIds = fleet.stream()
                .map(Vehicle::getId)
                .collect(Collectors.toSet());
        List<RentalPlan> plans = rentalPlanRepository.findAll();
        for (PlanType planType : List.of(PlanType.HOURLY, PlanType.DAILY, PlanType.MONTHLY)) {
            assertThat(plans)
                    .filteredOn(plan -> plan.getPlanType() == planType
                            && approvedVehicleIds.contains(plan.getVehicle().getId()))
                    .extracting(plan -> plan.getSlotPrice().doubleValue())
                    .hasSize(8)
                    .doesNotHaveDuplicates();
        }
    }

    private void assertSeed(String email, Role role) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(passwordEncoder.matches(DevDataSeeder.DEFAULT_PASSWORD, user.getPasswordHash())).isTrue();
    }
}
