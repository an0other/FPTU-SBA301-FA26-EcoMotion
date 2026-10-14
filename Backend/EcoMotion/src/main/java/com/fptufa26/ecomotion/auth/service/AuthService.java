package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;

import java.time.Instant;

public interface AuthService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);

    LoginResult issueSessionFor(String email, String rawPassword, String ipAddress, String userAgent);

    RefreshResult refresh(String refreshToken);

    void logout(String refreshToken);

    CurrentUserResponse getCurrentUser(Long userId);

    record LoginResult(LoginResponse response, String refreshToken, boolean rememberMe) {
    }

    record RefreshResult(RefreshResponse response, String refreshToken, boolean rememberMe, Instant expiresAt) {
    }
}
