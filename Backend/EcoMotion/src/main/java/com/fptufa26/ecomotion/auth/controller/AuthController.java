package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.dto.request.LoginRequest;
import com.fptufa26.ecomotion.auth.dto.request.RegisterRequest;
import com.fptufa26.ecomotion.auth.dto.request.SendOtpRequest;
import com.fptufa26.ecomotion.auth.dto.request.VerifyOtpRequest;
import com.fptufa26.ecomotion.auth.dto.response.CurrentUserResponse;
import com.fptufa26.ecomotion.auth.dto.response.LoginResponse;
import com.fptufa26.ecomotion.auth.dto.response.RefreshResponse;
import com.fptufa26.ecomotion.auth.dto.response.RegisterResponse;
import com.fptufa26.ecomotion.auth.dto.response.SendOtpResponse;
import com.fptufa26.ecomotion.auth.dto.response.VerifyOtpResponse;
import com.fptufa26.ecomotion.auth.service.AuthService;
import com.fptufa26.ecomotion.auth.service.RegistrationService;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final String cookieName;
    private final String cookiePath;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final Duration rememberMeCookieMaxAge;

    public AuthController(
            AuthService authService,
            RegistrationService registrationService,
            @Value("${app.cookie.name:refresh_token}") String cookieName,
            @Value("${app.cookie.path:/api/auth}") String cookiePath,
            @Value("${app.cookie.secure:false}") boolean cookieSecure,
            @Value("${app.cookie.same-site:Lax}") String cookieSameSite,
            @Value("${app.refresh-token.ttl-remember-me:7d}") Duration rememberMeCookieMaxAge) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.rememberMeCookieMaxAge = rememberMeCookieMaxAge;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(
                request,
                resolveClientIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshCookie(result.refreshToken(), result.rememberMe()).toString())
                .body(result.response());
    }

    @PostMapping("/register/send-otp")
    public ResponseEntity<SendOtpResponse> sendRegisterOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(registrationService.sendOtp(request.email()));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyRegisterOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(registrationService.verifyOtp(request.email(), request.otp()));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        RegisterResponse body = registrationService.register(
                request,
                resolveClientIp(httpRequest),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        // The account is created PENDING; no session is issued until activation completes.
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(readRefreshTokenCookie(httpRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(readRefreshTokenCookie(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal SecurityUser principal) {
        if (principal == null) {
            throw new UsernameNotFoundException("Authenticated principal is missing");
        }
        return ResponseEntity.ok(authService.getCurrentUser(principal.getUserId()));
    }

    private ResponseCookie buildRefreshCookie(String token, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .sameSite(cookieSameSite);

        if (rememberMe) {
            builder.maxAge(rememberMeCookieMaxAge);
        }
        return builder.build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();
    }

    private String readRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
