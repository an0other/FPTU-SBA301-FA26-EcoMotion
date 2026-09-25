package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.RefreshResponse;

public interface AuthService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);

    RefreshResponse refresh(String refreshToken);

    void logout(String refreshToken);

    CurrentUserResponse getCurrentUser(Long userId);

    record LoginResult(LoginResponse response, String refreshToken, boolean rememberMe) {
    }
}
