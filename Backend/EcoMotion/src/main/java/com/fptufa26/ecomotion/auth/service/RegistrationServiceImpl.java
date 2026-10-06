package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;
import com.fptufa26.ecomotion.auth.exception.DuplicateEmailException;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final String PENDING_MESSAGE =
            "Tài khoản đã được tạo. Vui lòng hoàn tất xác thực để kích hoạt.";

    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserService userService;
    private final CaptchaService captchaService;

    @Value("${app.otp.expose-in-response:false}")
    private boolean exposeOtpInResponse;

    @Value("${app.password.min-length:8}")
    private int passwordMinLength;

    @Override
    public SendOtpResponse sendOtp(String email) {
        // Fail fast: an address that already has an account can never complete registration, so
        // refuse before spending an OTP or sending a pointless email. Without this the duplicate
        // only surfaces at verify-otp or register, after the user has already waited for the code.
        // Trade-off: this lets a caller probe which addresses are registered — acceptable here
        // because the endpoint is protected by the OTP resend cooldown.
        if (userService.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        OtpService.OtpChallenge challenge = otpService.send(email);
        String exposed = exposeOtpInResponse ? challenge.code() : null;
        return new SendOtpResponse(true, challenge.expiresInSeconds(), challenge.resendCooldownSeconds(), exposed);
    }

    @Override
    public VerifyOtpResponse verifyOtp(String email, String otp) {
        // Throws on invalid/expired/too-many-attempts and consumes the entry on success.
        otpService.verify(email, otp);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String token = tokenService.issueRegistration(normalizedEmail);
        return new VerifyOtpResponse(token, tokenService.getRegistrationTtlSeconds());
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        // The client cannot be trusted to have solved the challenge: verify server-side first.
        captchaService.verify(request.captchaToken(), "register", ipAddress);

        TokenService.RegistrationPrincipal principal =
                tokenService.verifyRegistration(request.registrationToken());
        String email = principal.email();

        // Provider onboarding is not built yet — self-registration is RENTER-only for now.
        if (request.role() != Role.RENTER) {
            throw new UnsupportedRoleException();
        }
        if (!Boolean.TRUE.equals(request.agreeTerms())) {
            throw new IllegalArgumentException("Terms must be accepted");
        }
        if (request.password().length() < passwordMinLength) {
            throw new IllegalArgumentException("Password must be at least " + passwordMinLength + " characters");
        }
        if (userService.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        // No phone / full name yet: both are captured on the activation page, and the account
        // stays PENDING until that completes.
        User user = userService.createPendingUser(email, request.password(), request.role());
        userService.createRenterProfile(user);

        String activationToken = tokenService.issueActivation(user.getUserId(), email, request.role());
        return new RegisterResponse(
                true,
                PENDING_MESSAGE,
                activationToken,
                tokenService.getActivationTtlSeconds());
    }
}
