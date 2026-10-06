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
import org.springframework.web.multipart.MultipartFile;

/**
 * Drives the post-registration activation of a PENDING account as a three-step wizard, where each
 * step persists its own result so progress survives a reload or a backend restart:
 * <ol>
 *     <li>{@link #sendPhoneOtp} / {@link #verifyPhoneOtp} — verify the phone number and store it on
 *     {@code users.phone};</li>
 *     <li>{@link #scanCccd} — upload both CCCD sides, OCR the front and write the identity fields
 *     onto the renter profile;</li>
 *     <li>{@link #verifyFace} — upload a selfie, match it against the CCCD front portrait and, on a
 *     match, activate the account in the same call.</li>
 * </ol>
 * There is no in-memory draft: {@link #status} reconstructs the progress from the database, and the
 * steps enforce their order, so the face step is always the last one.
 *
 * <p>Because activation can also happen later, the wizard is reachable independently of the
 * post-registration redirect: {@link #requestResumeOtp} + {@link #resume} re-issue an activation
 * token for an existing PENDING account after confirming a fresh email OTP.</p>
 */
public interface ActivationService {

    SendPhoneOtpResponse sendPhoneOtp(SendPhoneOtpRequest request);

    VerifyPhoneOtpResponse verifyPhoneOtp(VerifyPhoneOtpRequest request);

    ActivationStatusResponse status(String activationToken);

    /** Stores both CCCD sides, persists the identity fields read from the front image. */
    ScanCccdResponse scanCccd(String activationToken, MultipartFile cccdFront, MultipartFile cccdBack);

    /**
     * Compares the uploaded selfie with the CCCD front portrait. On a match this is also the final
     * step: the selfie is stored and the account is flipped to ACTIVE.
     */
    FaceMatchResponse verifyFace(String activationToken, MultipartFile portrait);

    /** Sends an email OTP so a user can resume a PENDING activation at a later time. */
    SendOtpResponse requestResumeOtp(String email);

    /** Verifies the resume OTP and issues a fresh activation token for the PENDING account. */
    ResumeActivationResponse resume(String email, String otp);
}
