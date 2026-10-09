package com.fptufa26.ecomotion.auth.service.implement;

import com.fptufa26.ecomotion.auth.dto.response.FaceMatchResponse;
import com.fptufa26.ecomotion.auth.dto.response.ScanCccdResponse;
import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.auth.exception.AccountAlreadyActiveException;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.auth.exception.PhoneNotVerifiedException;
import com.fptufa26.ecomotion.auth.service.DocumentVerificationService;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RequiredArgsConstructor
@Service
public class DocumentVerificationServiceImpl implements DocumentVerificationService {

    private static final String RENTER = "renter";
    private static final String SCANNED_MESSAGE = "Đã quét CCCD. Vui lòng kiểm tra lại thông tin.";
    private static final String FACE_MATCHED_MESSAGE = "Xác thực khuôn mặt thành công.";
    private static final String FACE_MISMATCH_MESSAGE = "Khuôn mặt không khớp với ảnh trên CCCD.";
    private static final String CCCD_REQUIRED_MESSAGE = "Vui lòng quét CCCD trước khi xác thực khuôn mặt.";
    private static final String CCCD_IMAGE_MISSING_MESSAGE =
            "Ảnh CCCD không còn trên hệ thống. Vui lòng quét lại CCCD.";
    private static final String CCCD_FRONT_NAME = "cccd-front.jpg";
    private static final String PORTRAIT_NAME = "portrait.jpg";

    private final TokenService tokenService;
    private final FileStorageService fileStorageService;
    private final EkycService ekycService;
    private final UserService userService;

    @Override
    public ScanCccdResponse scanCccd(String activationToken, MultipartFile cccdFront) {
        User user = resolve(activationToken);
        requirePending(user);

        byte[] frontBytes = read(cccdFront);

        EkycService.OcrResult ocr = ekycService.ocrIdCard(frontBytes, CCCD_FRONT_NAME);
        requireIdentityFields(ocr);
        LocalDate dateOfBirth = parseDate(ocr.dateOfBirth());

        String frontPath = fileStorageService.store(
                RENTER, user.getUserId(), CCCD_FRONT_NAME, frontBytes, cccdFront.getContentType());

        userService.saveCccdIdentity(user.getUserId(), new UserService.CccdIdentity(
                ocr.fullName(),
                ocr.idNumber(),
                dateOfBirth,
                ocr.gender(),
                ocr.address(),
                frontPath));

        return new ScanCccdResponse(
                true,
                ocr.fullName(),
                ocr.idNumber(),
                dateOfBirth == null ? null : dateOfBirth.toString(),
                ocr.gender(),
                ocr.address(),
                frontPath,
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

    private void requirePending(User user) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AccountAlreadyActiveException();
        }
    }

    private void requireIdentityFields(EkycService.OcrResult ocr) {
        if (isBlank(ocr.idNumber())) {
            throw new EkycFailedException("Không đọc được số CCCD. Vui lòng chụp lại ảnh rõ hơn.");
        }
        if (isBlank(ocr.fullName())) {
            throw new EkycFailedException("Không đọc được họ tên trên CCCD. Vui lòng chụp lại ảnh rõ hơn.");
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
