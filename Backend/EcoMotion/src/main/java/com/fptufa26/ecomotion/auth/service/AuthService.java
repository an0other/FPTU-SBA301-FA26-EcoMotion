package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;

public interface AuthService {

    LoginResult login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * Issues a session for an already-authenticated caller, bypassing the captcha check. Used by
     * registration auto-login, where the identity was just established server-side in the same
     * transaction and no client challenge applies.
     */
    LoginResult issueSessionFor(String email, String rawPassword, String ipAddress, String userAgent);

    RefreshResponse refresh(String refreshToken);

    void logout(String refreshToken);

    CurrentUserResponse getCurrentUser(Long userId);

    record LoginResult(LoginResponse response, String refreshToken, boolean rememberMe) {
    }
}
