package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.dto.request.ActivationStatusRequest;
import com.fptufa26.ecomotion.auth.dto.request.ResumeActivationRequest;
import com.fptufa26.ecomotion.auth.dto.request.SendOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.SendPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyPhoneOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.ActivationStatusResponse;
import com.fptufa26.ecomotion.auth.dto.response.FaceMatchResponse;
import com.fptufa26.ecomotion.auth.dto.response.ResumeActivationResponse;
import com.fptufa26.ecomotion.auth.dto.response.ScanCccdResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyPhoneOtpResponse;
import com.fptufa26.ecomotion.auth.service.ActivationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Activation endpoints for a freshly registered PENDING account. They are public: the client holds
 * a short-lived activation token (returned by {@code POST /api/auth/register}) instead of a session,
 * because a PENDING account cannot log in yet.
 *
 * <p>The wizard walks three steps — verify the phone, scan the CCCD (OCR), then match the face —
 * and each step persists its own result, so the client can jump back to the first outstanding step
 * after a reload. The face step is the last one: a successful match activates the account in the
 * same call.</p>
 */
@RestController
@RequestMapping("/api/auth/activate")
public class ActivationController {

    private final ActivationService activationService;

    public ActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping("/send-phone-otp")
    public ResponseEntity<SendPhoneOtpResponse> sendPhoneOtp(@Valid @RequestBody SendPhoneOtpRequest request) {
        return ResponseEntity.ok(activationService.sendPhoneOtp(request));
    }

    @PostMapping("/verify-phone-otp")
    public ResponseEntity<VerifyPhoneOtpResponse> verifyPhoneOtp(@Valid @RequestBody VerifyPhoneOtpRequest request) {
        return ResponseEntity.ok(activationService.verifyPhoneOtp(request));
    }

    @PostMapping("/status")
    public ResponseEntity<ActivationStatusResponse> status(@Valid @RequestBody ActivationStatusRequest request) {
        return ResponseEntity.ok(activationService.status(request.activationToken()));
    }

    @PostMapping(value = "/scan-cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanCccdResponse> scanCccd(
            @RequestParam("activationToken") String activationToken,
            @RequestParam("cccdFront") MultipartFile cccdFront,
            @RequestParam("cccdBack") MultipartFile cccdBack) {
        return ResponseEntity.ok(activationService.scanCccd(activationToken, cccdFront, cccdBack));
    }

    @PostMapping(value = "/face-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceMatchResponse> verifyFace(
            @RequestParam("activationToken") String activationToken,
            @RequestParam("portrait") MultipartFile portrait) {
        return ResponseEntity.ok(activationService.verifyFace(activationToken, portrait));
    }

    /**
     * Resumes a PENDING activation from the standalone page: an OTP is emailed so the user can
     * continue later without the short-lived token handed out at registration time.
     */
    @PostMapping("/resume-otp")
    public ResponseEntity<SendOtpResponse> requestResumeOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(activationService.requestResumeOtp(request.email()));
    }

    @PostMapping("/resume")
    public ResponseEntity<ResumeActivationResponse> resume(@Valid @RequestBody ResumeActivationRequest request) {
        return ResponseEntity.ok(activationService.resume(request.email(), request.otp()));
    }
}
