package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.user.enums.Role;

public interface TokenService {

    String issueRegistration(String email);

    String issueActivation(Long userId, String email, Role role);

    RegistrationPrincipal verifyRegistration(String token);

    ActivationPrincipal verifyActivation(String token);

    long getRegistrationTtlSeconds();

    long getActivationTtlSeconds();

    record RegistrationPrincipal(String email) {
    }

    record ActivationPrincipal(Long userId, String email, Role role) {
    }
}
