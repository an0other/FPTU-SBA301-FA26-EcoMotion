package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEFAULT_PASSWORD = "EcoMotion@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seed("renter01@ecomotion.vn", "0901000001", "Renter 01", Role.RENTER);
        seed("provider01@ecomotion.vn", "0901000002", "Provider 01", Role.PROVIDER);
        seed("admin01@ecomotion.vn", "0901000003", "Admin 01", Role.ADMIN);
    }

    private void seed(String email, String phone, String fullName, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEkycStatus(EkycStatus.VERIFIED);
        userRepository.save(user);
    }
}
