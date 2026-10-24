package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.ActivationStatusResponse;
import com.fptufa26.ecomotion.auth.dto.response.ResumeActivationResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.NoPendingActivationException;
import com.fptufa26.ecomotion.auth.exception.PhoneAlreadyExistsException;
import com.fptufa26.ecomotion.auth.service.ActivationService;
import com.fptufa26.ecomotion.otp.service.OtpService;
import com.fptufa26.ecomotion.notification.external.enums.NotificationChannel;
import com.fptufa26.ecomotion.otp.dto.request.OtpSendRequest;
import com.fptufa26.ecomotion.otp.dto.request.OtpVerifyRequest;
import com.fptufa26.ecomotion.otp.dto.response.OtpChallenge;
import com.fptufa26.ecomotion.otp.enums.OtpPurpose;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ActivationServiceImpl implements ActivationService {

    private final TokenService tokenService;
    private final OtpService otpService;
    private final UserService userService;

    @Value("${app.otp.expose-in-response:false}")
    private boolean exposeOtpInResponse;

    @Override
    public SendPhoneOtpResponse sendPhoneOtp(SendPhoneOtpRequest request) {
        User user = resolve(request.activationToken());
        requirePending(user);
        String phone = request.phone().trim();

        if (!phone.equals(user.getPhone()) && userService.existsByPhone(phone)) {
            throw new PhoneAlreadyExistsException();
        }

        OtpChallenge challenge = otpService.send(new OtpSendRequest(
                String.valueOf(user.getUserId()),
                phone,
                OtpPurpose.PHONE_VERIFICATION,
                NotificationChannel.SMS));
        return new SendPhoneOtpResponse(
                true, challenge.code(), challenge.expiresInSeconds(), challenge.resendCooldownSeconds());
    }

    @Override
    public VerifyPhoneOtpResponse verifyPhoneOtp(VerifyPhoneOtpRequest request) {
        User user = resolve(request.activationToken());
        requirePending(user);

        String phone = otpService.verify(new OtpVerifyRequest(
                String.valueOf(user.getUserId()),
                request.otp(),
                OtpPurpose.PHONE_VERIFICATION)).recipient();
        userService.assignPhone(user.getUserId(), phone);
        return new VerifyPhoneOtpResponse(true, phone);
    }

    @Override
    public ActivationStatusResponse status(String activationToken) {
        User user = resolve(activationToken);
        Renter renter = userService.findRenterProfile(user.getUserId()).orElse(null);
        boolean phoneVerified = !isBlank(user.getPhone());
        boolean cccdScanned = renter != null && !isBlank(renter.getCccdNo());
        return new ActivationStatusResponse(
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getEkycStatus().name(),
                phoneVerified,
                cccdScanned,
                user.getFullName(),
                renter == null ? null : renter.getCccdNo(),
                renter == null || renter.getDateOfBirth() == null ? null : renter.getDateOfBirth().toString(),
                renter == null ? null : renter.getGender(),
                renter == null ? null : renter.getAddress());
    }

    @Override
    public SendOtpResponse requestResumeOtp(String email) {
        User user = findResumableUser(email);
        OtpChallenge challenge = otpService.send(new OtpSendRequest(
                user.getEmail(),
                user.getEmail(),
                OtpPurpose.ACTIVATION_RESUME,
                NotificationChannel.EMAIL));
        String exposed = exposeOtpInResponse ? challenge.code() : null;
        return new SendOtpResponse(
                true, challenge.expiresInSeconds(), challenge.resendCooldownSeconds(), exposed);
    }

    @Override
    public ResumeActivationResponse resume(String email, String otp) {
        otpService.verify(new OtpVerifyRequest(email, otp, OtpPurpose.ACTIVATION_RESUME));

        User user = findResumableUser(email);
        String activationToken = tokenService.issueActivation(user.getUserId(), user.getEmail(), user.getRole());
        return new ResumeActivationResponse(
                activationToken,
                tokenService.getActivationTtlSeconds(),
                user.getEmail(),
                user.getFullName());
    }

    private User findResumableUser(String email) {
        User user = userService.findByEmail(email)
                .orElseThrow(NoPendingActivationException::new);
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AccountAlreadyActiveException();
        }
        if (user.getStatus() != UserStatus.PENDING) {
            throw new NoPendingActivationException();
        }
        return user;
    }

    private void requirePending(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AccountAlreadyActiveException();
        }
    }

    private User resolve(String activationToken) {
        TokenService.ActivationPrincipal principal = tokenService.verifyActivation(activationToken);
        return userService.findById(principal.userId())
                .orElseThrow(ActivationTokenInvalidException::new);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
