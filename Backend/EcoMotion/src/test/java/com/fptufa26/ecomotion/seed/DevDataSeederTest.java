package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the dev-profile seed data required by plan §11 Phase 4. */
@SpringBootTest
@ActiveProfiles("dev")
class DevDataSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Test
    @DisplayName("three test accounts exist with the documented emails, roles and password")
    void seedsThreeAccounts() {
        assertSeed("renter01@ecomotion.vn", Role.RENTER);
        assertSeed("provider01@ecomotion.vn", Role.PROVIDER);
        assertSeed("admin01@ecomotion.vn", Role.ADMIN);
    }

    @Test
    @DisplayName("re-running the seeder does not duplicate accounts")
    void isIdempotent() {
        long before = userRepository.count();

        devDataSeeder.run(null);

        assertThat(userRepository.count()).isEqualTo(before);
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
