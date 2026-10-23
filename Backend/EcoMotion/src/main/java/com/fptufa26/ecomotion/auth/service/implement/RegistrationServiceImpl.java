package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.captcha.CaptchaVerifier;
import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;
import com.fptufa26.ecomotion.auth.exception.DuplicateEmailException;
import com.fptufa26.ecomotion.auth.exception.UnsupportedRoleException;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.dto.response.OtpChallenge;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.auth.service.RegistrationService;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.user.enums.Role;
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
    private final CaptchaVerifier captchaVerifier;

    @Value("${app.otp.expose-in-response:false}")
    private boolean exposeOtpInResponse;

    @Value("${app.password.min-length:8}")
    private int passwordMinLength;

    @Override
    public SendOtpResponse sendOtp(String email) {
        if (userService.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        OtpChallenge challenge = otpService.send(new OtpSendRequest(
                email, email, OtpPurpose.REGISTRATION, NotificationChannel.EMAIL));
        String exposed = exposeOtpInResponse ? challenge.code() : null;
        return new SendOtpResponse(true, challenge.expiresInSeconds(), challenge.resendCooldownSeconds(), exposed);
    }

    @Override
    public VerifyOtpResponse verifyOtp(String email, String otp) {
        otpService.verify(new OtpVerifyRequest(email, otp, OtpPurpose.REGISTRATION));
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String token = tokenService.issueRegistration(normalizedEmail);
        return new VerifyOtpResponse(token, tokenService.getRegistrationTtlSeconds());
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        captchaVerifier.verify(request.captchaToken(), "register", ipAddress);

        TokenService.RegistrationPrincipal principal =
                tokenService.verifyRegistration(request.registrationToken());
        String email = principal.email();

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
