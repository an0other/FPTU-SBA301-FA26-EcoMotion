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
import com.fptufa26.ecomotion.auth.service.DocumentVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/activate")
public class ActivationController {

    private final ActivationService activationService;
    private final DocumentVerificationService documentVerificationService;

    public ActivationController(
            ActivationService activationService,
            DocumentVerificationService documentVerificationService) {
        this.activationService = activationService;
        this.documentVerificationService = documentVerificationService;
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
            @RequestParam("cccdFront") MultipartFile cccdFront) {
        return ResponseEntity.ok(documentVerificationService.scanCccd(activationToken, cccdFront));
    }

    @PostMapping(value = "/face-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceMatchResponse> verifyFace(
            @RequestParam("activationToken") String activationToken,
            @RequestParam("portrait") MultipartFile portrait) {
        return ResponseEntity.ok(documentVerificationService.verifyFace(activationToken, portrait));
    }

    @PostMapping("/resume-otp")
    public ResponseEntity<SendOtpResponse> requestResumeOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(activationService.requestResumeOtp(request.email()));
    }

    @PostMapping("/resume")
    public ResponseEntity<ResumeActivationResponse> resume(@Valid @RequestBody ResumeActivationRequest request) {
        return ResponseEntity.ok(activationService.resume(request.email(), request.otp()));
    }
}
