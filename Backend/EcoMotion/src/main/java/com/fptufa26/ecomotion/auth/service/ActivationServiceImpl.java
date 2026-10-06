package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.ActivationStatusResponse;
import com.fptufa26.ecomotion.auth.dto.response.FaceMatchResponse;
import com.fptufa26.ecomotion.auth.dto.response.ResumeActivationResponse;
import com.fptufa26.ecomotion.auth.dto.response.ScanCccdResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.EkycFailedException;
import com.fptufa26.ecomotion.auth.exception.FileStorageException;
import com.fptufa26.ecomotion.auth.exception.NoPendingActivationException;
import com.fptufa26.ecomotion.auth.exception.PhoneAlreadyExistsException;
import com.fptufa26.ecomotion.auth.exception.PhoneNotVerifiedException;
import com.fptufa26.ecomotion.auth.ekyc.EkycService;
import com.fptufa26.ecomotion.auth.storage.FileStorageService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class ActivationServiceImpl implements ActivationService {

    private static final String RENTER = "renter";
    private static final String SCANNED_MESSAGE = "Đã quét CCCD. Vui lòng kiểm tra lại thông tin.";
    private static final String FACE_MATCHED_MESSAGE = "Xác thực khuôn mặt thành công.";
    private static final String FACE_MISMATCH_MESSAGE = "Khuôn mặt không khớp với ảnh trên CCCD.";
    private static final String CCCD_REQUIRED_MESSAGE = "Vui lòng quét CCCD trước khi xác thực khuôn mặt.";
    private static final String CCCD_IMAGE_MISSING_MESSAGE =
            "Ảnh CCCD không còn trên hệ thống. Vui lòng quét lại CCCD.";
    private static final String CCCD_FRONT_NAME = "cccd-front.jpg";
    private static final String CCCD_BACK_NAME = "cccd-back.jpg";
    private static final String PORTRAIT_NAME = "portrait.jpg";

    private final TokenService tokenService;
    private final OtpService otpService;
    private final FileStorageService fileStorageService;
    private final EkycService ekycService;
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

        OtpService.PhoneOtpChallenge challenge =
                otpService.sendPhone(String.valueOf(user.getUserId()), phone);
        return new SendPhoneOtpResponse(
                true, challenge.code(), challenge.expiresInSeconds(), challenge.resendCooldownSeconds());
    }

    @Override
    public VerifyPhoneOtpResponse verifyPhoneOtp(VerifyPhoneOtpRequest request) {
        User user = resolve(request.activationToken());
        requirePending(user);

        String phone = otpService.verifyPhone(String.valueOf(user.getUserId()), request.otp());
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
    public ScanCccdResponse scanCccd(String activationToken, MultipartFile cccdFront, MultipartFile cccdBack) {
        User user = resolve(activationToken);
        requirePending(user);

        byte[] frontBytes = read(cccdFront);
        byte[] backBytes = read(cccdBack);

        EkycService.OcrResult ocr = ekycService.ocrIdCard(frontBytes, CCCD_FRONT_NAME);
        if (isBlank(ocr.fullName()) && isBlank(ocr.idNumber())) {
            throw new EkycFailedException("Không đọc được thông tin trên CCCD. Vui lòng chụp lại ảnh rõ hơn.");
        }
        LocalDate dateOfBirth = parseDate(ocr.dateOfBirth());

        String frontPath = fileStorageService.store(
                RENTER, user.getUserId(), CCCD_FRONT_NAME, frontBytes, cccdFront.getContentType());
        String backPath = fileStorageService.store(
                RENTER, user.getUserId(), CCCD_BACK_NAME, backBytes, cccdBack.getContentType());

        userService.saveCccdIdentity(user.getUserId(), new UserService.CccdIdentity(
                ocr.fullName(),
                ocr.idNumber(),
                dateOfBirth,
                ocr.gender(),
                ocr.address(),
                frontPath,
                backPath));

        return new ScanCccdResponse(
                true,
                ocr.fullName(),
                ocr.idNumber(),
                dateOfBirth == null ? null : dateOfBirth.toString(),
                ocr.gender(),
                ocr.address(),
                frontPath,
                backPath,
                SCANNED_MESSAGE);
    }

    @Override
    public FaceMatchResponse verifyFace(String activationToken, MultipartFile portrait) {
        User user = resolve(activationToken);
        requirePending(user);

        if (isBlank(user.getPhone())) {
            throw new PhoneNotVerifiedException();
        }
        Renter renter = userService.findRenterProfile(user.getUserId())
                .filter(r -> !isBlank(r.getCccdNo()) && !isBlank(r.getCccdImageUrl()))
                .orElseThrow(() -> new EkycFailedException(CCCD_REQUIRED_MESSAGE));

        byte[] portraitBytes = read(portrait);
        byte[] frontBytes;
        try {
            frontBytes = fileStorageService.read(renter.getCccdImageUrl());
        } catch (FileStorageException ex) {
            throw new EkycFailedException(CCCD_IMAGE_MISSING_MESSAGE);
        }

        EkycService.FaceMatchResult face =
                ekycService.faceMatch(frontBytes, CCCD_FRONT_NAME, portraitBytes, PORTRAIT_NAME);

        if (!face.match()) {
            return new FaceMatchResponse(false, face.similarity(), null, false, FACE_MISMATCH_MESSAGE);
        }

        String portraitPath = fileStorageService.store(
                RENTER, user.getUserId(), PORTRAIT_NAME, portraitBytes, portrait.getContentType());
        userService.activateWithPortrait(user.getUserId(), portraitPath);

        return new FaceMatchResponse(true, face.similarity(), portraitPath, true, FACE_MATCHED_MESSAGE);
    }

    @Override
    public SendOtpResponse requestResumeOtp(String email) {
        User user = findResumableUser(email);
        OtpService.OtpChallenge challenge = otpService.send(user.getEmail());
        String exposed = exposeOtpInResponse ? challenge.code() : null;
        return new SendOtpResponse(
                true, challenge.expiresInSeconds(), challenge.resendCooldownSeconds(), exposed);
    }

    @Override
    public ResumeActivationResponse resume(String email, String otp) {
        otpService.verify(email, otp);

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

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("A required image is missing");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the uploaded file", ex);
        }
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
