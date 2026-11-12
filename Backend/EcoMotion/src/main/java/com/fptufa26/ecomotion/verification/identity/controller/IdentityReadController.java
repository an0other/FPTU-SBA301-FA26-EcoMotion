package com.fptufa26.ecomotion.verification.identity.controller;

import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityConstraintsResponse;
import com.fptufa26.ecomotion.verification.identity.service.IdentityReadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class IdentityReadController {
    private final IdentityReadService reads;
    private final TokenService tokens;
    private final UserService users;

    @GetMapping("/api/identity/constraints")
    public IdentityConstraintsResponse constraints() { return reads.constraints(); }

    @GetMapping("/api/identity/status")
    @PreAuthorize("hasAnyAuthority('ROLE_RENTER','ROLE_PROVIDER','ROLE_PROVIDER_ONBOARDING')")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityStatusResponse status(@AuthenticationPrincipal SecurityUser user) { return reads.status(user.getUserId()); }

    @PostMapping("/api/auth/activate/identity-status")
    public com.fptufa26.ecomotion.verification.identity.dto.response.IdentityStatusResponse activationStatus(@Valid @RequestBody Activation request) {
        var principal = tokens.verifyActivation(request.activationToken());
        var user = users.findById(principal.userId()).orElseThrow(ActivationTokenInvalidException::new);
        // Read-only status remains available to the valid activation token after approval.
        // Mutation/evidence endpoints still require PENDING or an authenticated account.
        if ((user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.ACTIVE) || user.getRole() != principal.role()
                || !user.getEmail().equals(principal.email())) throw new ActivationTokenInvalidException();
        return reads.status(user.getId());
    }

    public record Activation(@NotBlank String activationToken) { }
}
