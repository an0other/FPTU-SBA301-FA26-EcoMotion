package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.auth.exception.RegistrationTokenInvalidException;
import com.fptufa26.ecomotion.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Short-lived handoff tokens used during registration and activation. Both are signed with the
 * access-token secret but kept distinct by a {@code sub} prefix and a {@code purpose} claim, so
 * neither can ever be replayed as an access token (or as the other handoff token).
 */
@Component
public class TokenService {

    private static final String REGISTRATION_PREFIX = "registration:";
    private static final String REGISTRATION_PURPOSE = "REGISTER";
    private static final String ACTIVATION_PREFIX = "activation:";
    private static final String ACTIVATION_PURPOSE = "ACTIVATE";

    private final SecretKey key;
    private final Duration registrationTtl;
    private final Duration activationTtl;
    private final String issuer;

    public TokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.registration-token.ttl:30m}") Duration registrationTtl,
            @Value("${app.activation-token.ttl:60m}") Duration activationTtl,
            @Value("${app.jwt.issuer:ecomotion}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.registrationTtl = registrationTtl;
        this.activationTtl = activationTtl;
        this.issuer = issuer;
    }

    /** Binds a verified OTP to the final register call for the given address. */
    public String issueRegistration(String email) {
        return issue(REGISTRATION_PREFIX + email, REGISTRATION_PURPOSE, registrationTtl, null, null);
    }

    /** Identifies the freshly created PENDING account so the activation page can complete it. */
    public String issueActivation(Long userId, String email, Role role) {
        return issue(ACTIVATION_PREFIX + userId, ACTIVATION_PURPOSE, activationTtl, email, role);
    }

    public RegistrationPrincipal verifyRegistration(String token) {
        Claims claims = parse(token, REGISTRATION_PURPOSE, REGISTRATION_PREFIX,
                RegistrationTokenInvalidException::new);
        return new RegistrationPrincipal(claims.getSubject().substring(REGISTRATION_PREFIX.length()));
    }

    public ActivationPrincipal verifyActivation(String token) {
        Claims claims = parse(token, ACTIVATION_PURPOSE, ACTIVATION_PREFIX,
                ActivationTokenInvalidException::new);
        String role = claims.get("role", String.class);
        return new ActivationPrincipal(
                Long.valueOf(claims.getSubject().substring(ACTIVATION_PREFIX.length())),
                claims.get("email", String.class),
                role == null ? null : Role.valueOf(role));
    }

    public long getRegistrationTtlSeconds() {
        return registrationTtl.toSeconds();
    }

    public long getActivationTtlSeconds() {
        return activationTtl.toSeconds();
    }

    private String issue(String subject, String purpose, Duration ttl, String email, Role role) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("purpose", purpose)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .id(UUID.randomUUID().toString());
        if (email != null) {
            builder.claim("email", email);
        }
        if (role != null) {
            builder.claim("role", role.name());
        }
        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    private Claims parse(String token, String purpose, String prefix,
                         java.util.function.Supplier<RuntimeException> onInvalid) {
        if (token == null || token.isBlank()) {
            throw onInvalid.get();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            if (subject == null
                    || !subject.startsWith(prefix)
                    || !purpose.equals(claims.get("purpose", String.class))) {
                throw onInvalid.get();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw onInvalid.get();
        }
    }

    public record RegistrationPrincipal(String email) {
    }

    public record ActivationPrincipal(Long userId, String email, Role role) {
    }
}
