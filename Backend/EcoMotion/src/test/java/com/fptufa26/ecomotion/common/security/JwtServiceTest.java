package com.fptufa26.ecomotion.common.security;

import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link JwtService} (plan §11 Phase 5, §12 case 16). */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-unit-and-integration-tests-32bytes";
    private static final String ISSUER = "ecomotion";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15), ISSUER);

    private static User sampleUser() {
        User user = new User();
        user.setUserId(42L);
        user.setEmail("renter01@ecomotion.vn");
        user.setPasswordHash("$2a$12$irrelevant");
        user.setRole(Role.RENTER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    @Test
    @DisplayName("access token carries sub, role, iss, iat, exp and jti")
    void generatesTokenWithExpectedClaims() {
        String token = jwtService.generateAccessToken(sampleUser());

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("RENTER");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("sensitive fields are never embedded in the token")
    void doesNotLeakSensitiveClaims() {
        String token = jwtService.generateAccessToken(sampleUser());

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.get("password_hash")).isNull();
        assertThat(claims.get("passwordHash")).isNull();
        assertThat(claims.get("email")).isNull();
        assertThat(claims.get("username")).isNull();
        assertThat(claims.get("status")).isNull();
    }

    @Test
    @DisplayName("a valid token resolves back to its user id")
    void extractsUserId() {
        String token = jwtService.generateAccessToken(sampleUser());

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("expired token is rejected")
    void rejectsExpiredToken() {
        JwtService shortLived = new JwtService(SECRET, Duration.ofMinutes(-5), ISSUER);
        String expired = shortLived.generateAccessToken(sampleUser());

        assertThat(jwtService.isTokenValid(expired)).isFalse();
        assertThatThrownBy(() -> jwtService.parseClaims(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("token signed with another key is rejected")
    void rejectsTokenWithWrongSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-32-bytes!!".getBytes(StandardCharsets.UTF_8));

        String foreignToken = Jwts.builder()
                .subject("42")
                .claim("role", "RENTER")
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("token issued by another issuer is rejected")
    void rejectsTokenFromForeignIssuer() {
        JwtService otherIssuer = new JwtService(SECRET, Duration.ofMinutes(15), "someone-else");
        String token = otherIssuer.generateAccessToken(sampleUser());

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("garbage input is rejected instead of throwing")
    void rejectsMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }
}
