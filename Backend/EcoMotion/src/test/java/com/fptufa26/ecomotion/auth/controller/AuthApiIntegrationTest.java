package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.entity.RefreshToken;
import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the auth API over the real filter chain, real JPA repositories and an
 * in-memory database.
 *
 * <p>Covers the 16 mandatory scenarios of plan §12.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd";
    private static final String SECRET = "test-secret-key-for-unit-and-integration-tests-32bytes";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String GENERIC_MESSAGE = "Invalid email or password";

    private static final String RENTER_EMAIL = "renter01@ecomotion.vn";
    private static final String PROVIDER_EMAIL = "provider01@ecomotion.vn";
    private static final String ADMIN_EMAIL = "admin01@ecomotion.vn";

    /**
     * Pre-computed once with a low BCrypt cost. The production encoder (strength 12) still
     * verifies it, because BCrypt stores the cost inside the hash — this just keeps the suite fast.
     */
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void seedUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        insertUser(RENTER_EMAIL, Role.RENTER, UserStatus.ACTIVE);
        insertUser(PROVIDER_EMAIL, Role.PROVIDER, UserStatus.ACTIVE);
        insertUser(ADMIN_EMAIL, Role.ADMIN, UserStatus.ACTIVE);
        insertUser("inactive01@ecomotion.vn", Role.RENTER, UserStatus.INACTIVE);
    }

    private void insertUser(String email, Role role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(PASSWORD_HASH);
        user.setRole(role);
        user.setStatus(status);
        userRepository.save(user);
    }

    // ------------------------------------------------------------- test cases 1-3

    @Test
    @DisplayName("case 1: login by email with rememberMe=true sets Max-Age=604800")
    void case1_loginByEmailWithRememberMe() throws Exception {
        LoginOutcome outcome = login(RENTER_EMAIL, PASSWORD, true);

        String setCookie = outcome.result().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(REFRESH_COOKIE + "=")
                .contains("Path=/api/auth")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Max-Age=604800");
        assertThat(outcome.result().getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isEqualTo(604800);

        JsonNode body = readJson(outcome.result());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(body.get("user").get("email").asText()).isEqualTo(RENTER_EMAIL);
        assertThat(body.get("user").get("role").asText()).isEqualTo("RENTER");
        assertThat(body.get("user").get("status").asText()).isEqualTo("ACTIVE");
        // There is no username field anywhere in the API any more.
        assertThat(body.get("user").has("username")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();
    }

    @Test
    @DisplayName("case 2: login with rememberMe=false sets a session cookie (no Max-Age)")
    void case2_loginWithoutRememberMe() throws Exception {
        LoginOutcome outcome = login(RENTER_EMAIL, PASSWORD, false);

        String setCookie = outcome.result().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").doesNotContain("Max-Age");
        assertThat(outcome.result().getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isEqualTo(-1);
    }

    @Test
    @DisplayName("case 3: email lookup is case-insensitive")
    void case3_loginWithUppercaseEmail() throws Exception {
        LoginOutcome outcome = login("RENTER01@ECOMOTION.VN", PASSWORD, false);

        assertThat(readJson(outcome.result()).get("user").get("email").asText()).isEqualTo(RENTER_EMAIL);
    }

    // ------------------------------------------------------------- test cases 4-6

    @Test
    @DisplayName("case 4: wrong password yields 401 with the generic message")
    void case4_loginWithWrongPassword() throws Exception {
        mockMvc.perform(loginRequest(RENTER_EMAIL, "wrong-password", false))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE))
                .andExpect(jsonPath("$.path").value(LOGIN_PATH))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("case 5: unknown email yields 401 with the same message as case 4")
    void case5_loginWithUnknownEmail() throws Exception {
        String unknownMessage = mockMvc.perform(loginRequest("ghost@ecomotion.vn", PASSWORD, false))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String wrongPasswordMessage = mockMvc.perform(loginRequest(RENTER_EMAIL, "wrong-password", false))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(objectMapper.readTree(unknownMessage).get("message").asText())
                .isEqualTo(objectMapper.readTree(wrongPasswordMessage).get("message").asText())
                .isEqualTo(GENERIC_MESSAGE);
    }

    @Test
    @DisplayName("case 6: inactive account yields 403")
    void case6_loginWithInactiveAccount() throws Exception {
        mockMvc.perform(loginRequest("inactive01@ecomotion.vn", PASSWORD, false))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Account is inactive"));
    }

    // ------------------------------------------------------------ test cases 7-10

    @Test
    @DisplayName("case 7: refresh with a valid cookie issues a new access token")
    void case7_refreshWithValidCookie() throws Exception {
        LoginOutcome outcome = login(RENTER_EMAIL, PASSWORD, true);

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(outcome)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        String newToken = readJson(refreshed).get("accessToken").asText();
        assertThat(newToken).isNotBlank().isNotEqualTo(outcome.accessToken());
        // No rotation: the cookie is deliberately not re-issued.
        assertThat(refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("case 8: refresh with a revoked cookie yields 401")
    void case8_refreshWithRevokedCookie() throws Exception {
        LoginOutcome outcome = login(RENTER_EMAIL, PASSWORD, true);

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(outcome)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie(outcome)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("case 9: refresh with an expired cookie yields 401")
    void case9_refreshWithExpiredCookie() throws Exception {
        User user = userRepository.findByEmailIgnoreCase(RENTER_EMAIL).orElseThrow();
        String rawToken = "an-opaque-token-that-already-expired";

        RefreshToken expired = new RefreshToken();
        expired.setUser(user);
        expired.setTokenHash(sha256Hex(rawToken));
        expired.setExpiresAt(Instant.now().minus(Duration.ofHours(2)));
        expired.setCreatedAt(Instant.now().minus(Duration.ofDays(2)));
        refreshTokenRepository.save(expired);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, rawToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("case 10: refresh without a cookie yields 401")
    void case10_refreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------------------------------------------------------------- test case 11

    @Test
    @DisplayName("case 11: logout clears the cookie and stamps revoked_at")
    void case11_logout() throws Exception {
        LoginOutcome outcome = login(RENTER_EMAIL, PASSWORD, true);

        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie(outcome)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("Max-Age=0").contains("Path=/api/auth").contains("HttpOnly");
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero();

        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(outcome.refreshToken()))
                .orElseThrow();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    // ------------------------------------------------------------ test cases 12-15

    @Test
    @DisplayName("case 12: /api/auth/me without a token yields a JSON 401, not HTML")
    void case12_meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    @DisplayName("case 13: role-based URL rules return JSON 403 / 200 as appropriate")
    void case13_roleBasedAccessControl() throws Exception {
        String renterToken = login(RENTER_EMAIL, PASSWORD, false).accessToken();
        String providerToken = login(PROVIDER_EMAIL, PASSWORD, false).accessToken();
        String adminToken = login(ADMIN_EMAIL, PASSWORD, false).accessToken();

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(providerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/provider/ping").header(HttpHeaders.AUTHORIZATION, bearer(providerToken)))
                .andExpect(status().isOk());

        // ADMIN is allowed on provider endpoints too.
        mockMvc.perform(get("/api/provider/ping").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/renter/ping").header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/renter/ping").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("case 14: an expired access token yields 401")
    void case14_expiredAccessToken() throws Exception {
        User user = userRepository.findByEmailIgnoreCase(RENTER_EMAIL).orElseThrow();
        JwtService expiredIssuer = new JwtService(SECRET, Duration.ofMinutes(-5), "ecomotion");
        String expiredToken = expiredIssuer.generateAccessToken(user);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("case 15: deactivating a user takes effect immediately, before the token expires")
    void case15_deactivationTakesEffectImmediately() throws Exception {
        String token = login(RENTER_EMAIL, PASSWORD, false).accessToken();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailIgnoreCase(RENTER_EMAIL).orElseThrow();
        user.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(user);

        // The JWT is still well within its 15-minute lifetime — the filter reloading the user
        // from the database is what makes this fail right away.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- test case 16

    @Test
    @DisplayName("case 16: the access token payload contains the documented claims")
    void case16_accessTokenClaims() throws Exception {
        String token = login(RENTER_EMAIL, PASSWORD, false).accessToken();

        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo(
                String.valueOf(userRepository.findByEmailIgnoreCase(RENTER_EMAIL).orElseThrow().getUserId()));
        assertThat(claims.get("role", String.class)).isEqualTo("RENTER");
        assertThat(claims.getIssuer()).isEqualTo("ecomotion");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getId()).isNotBlank();
        // The email is a sensitive field and must never travel inside the token.
        assertThat(claims.get("email")).isNull();
        assertThat(claims.get("username")).isNull();
    }

    // -------------------------------------------------------------- /me happy path

    @Test
    @DisplayName("GET /api/auth/me returns the fresh user record including last_login_at")
    void meReturnsCurrentUser() throws Exception {
        String token = login(ADMIN_EMAIL, PASSWORD, false).accessToken();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    @DisplayName("login rejects a body with missing fields as 400")
    void loginValidationRejectsMissingFields() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("login rejects a malformed email address as 400")
    void loginValidationRejectsMalformedEmail() throws Exception {
        mockMvc.perform(loginRequest("not-an-email", PASSWORD, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("login accepts a body that omits rememberMe, defaulting it to false")
    void loginWithoutRememberMeFieldSucceeds() throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", RENTER_EMAIL,
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        // rememberMe omitted -> treated as false -> session cookie, i.e. no Max-Age.
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).doesNotContain("Max-Age");
        assertThat(readJson(result).get("accessToken").asText()).isNotBlank();
    }

    // ------------------------------------------------------------- OpenAPI contract

    @Test
    @DisplayName("Swagger exposes all auth endpoints plus the bearerAuth scheme")
    void openApiDocumentIsExposed() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode document = objectMapper.readTree(apiDocs);
        assertThat(document.at("/paths").has("/api/auth/login")).isTrue();
        assertThat(document.at("/paths").has("/api/auth/refresh")).isTrue();
        assertThat(document.at("/paths").has("/api/auth/logout")).isTrue();
        assertThat(document.at("/paths").has("/api/auth/me")).isTrue();
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
    }

    // -------------------------------------------------------------------- helpers

    private record LoginOutcome(String accessToken, String refreshToken, MvcResult result) {
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String email, String password, boolean rememberMe) throws Exception {
        return post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password,
                        "rememberMe", rememberMe)));
    }

    private LoginOutcome login(String email, String password, boolean rememberMe) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(email, password, rememberMe))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new LoginOutcome(
                readJson(result).get("accessToken").asText(),
                cookie == null ? null : cookie.getValue(),
                result);
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static Cookie refreshCookie(LoginOutcome outcome) {
        return new Cookie(REFRESH_COOKIE, outcome.refreshToken());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
