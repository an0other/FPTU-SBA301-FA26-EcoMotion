package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.FirstLoginChangePasswordRequest;
import com.fptufa26.ecomotion.auth.dto.request.ResetPasswordRequest;

public interface PasswordResetService {
    void sendOtp(String email);
    void reset(ResetPasswordRequest request);
    void changeTemporaryPassword(FirstLoginChangePasswordRequest request);
}
