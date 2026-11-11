package com.fptufa26.ecomotion.verification.identity.controller;

import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse;
import com.fptufa26.ecomotion.verification.identity.service.IdentityAiAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class IdentityAiAssessmentController {

    private final IdentityAiAssessmentService assessments;
    private final TokenService tokens;
    private final UserService users;

    @PostMapping(value = "/api/identity/assessments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER', 'ROLE_PROVIDER', 'ROLE_PROVIDER_ONBOARDING')")
    public IdentityAiAssessmentResponse assessAuthenticated(
            @AuthenticationPrincipal SecurityUser principal,
            @RequestParam MultipartFile cccdFront,
            @RequestParam MultipartFile cccdBack,
            @RequestParam MultipartFile video) {
        return assessments.assess(principal.getUserId(), cccdFront, cccdBack, video);
    }

    @PostMapping(value = "/api/auth/activate/identity-assessment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdentityAiAssessmentResponse assessActivation(
            @RequestParam String activationToken,
            @RequestParam MultipartFile cccdFront,
            @RequestParam MultipartFile cccdBack,
            @RequestParam MultipartFile video) {
        var principal = tokens.verifyActivation(activationToken);
        var user = users.findById(principal.userId()).orElseThrow(ActivationTokenInvalidException::new);
        if (user.getStatus() != UserStatus.PENDING || user.getRole() != principal.role()
                || !user.getEmail().equals(principal.email())) {
            throw new ActivationTokenInvalidException();
        }
        return assessments.assess(user.getUserId(), cccdFront, cccdBack, video);
    }
}
