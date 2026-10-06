package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.user.entity.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.entity.UserStatus;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the simplified registration API: email OTP + role/email/password only.
 * A successful register creates a PENDING account and returns an activation token — no session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.otp.provider=logging",
        "app.otp.expose-in-response=true",
        "app.otp.resend-cooldown=60s",
        "app.otp.max-attempts=5"
})
class RegistrationApiIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd123";
    private static final String SEND_PATH = "/api/auth/register/send-otp";
    private static final String VERIFY_PATH = "/api/auth/register/verify-otp";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String REFRESH_COOKIE = "refresh_token";

    private static final String EXISTING_EMAIL = "taken@ecomotion.vn";
    private static final String EXISTING_PHONE = "0909999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RenterRepository renterRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void seed() {
        refreshTokenRepository.deleteAll();
        renterRepository.deleteAll();
        userRepository.deleteAll();

        User existing = new User();
        existing.setEmail(EXISTING_EMAIL);
        existing.setPhone(EXISTING_PHONE);
        existing.setFullName("Existing User");
        existing.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
        existing.setRole(Role.RENTER);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setEkycStatus(EkycStatus.VERIFIED);
        userRepository.save(existing);
    }

    // ------------------------------------------------------------- happy path

    @Test
    @DisplayName("renter happy path: 201, PENDING account, renter row, activation token, no cookie")
    void renterHappyPath() throws Exception {
        String email = "newrenter@ecomotion.vn";
        String token = verifiedToken(email);

        MvcResult result = mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.activationToken").isNotEmpty())
                .andReturn();

        // No session is issued at registration.
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.getEkycStatus()).isEqualTo(EkycStatus.UNVERIFIED);
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getFullName()).isNull();
        assertThat(saved.getRole()).isEqualTo(Role.RENTER);
        assertThat(renterRepository.existsByUser_Id(saved.getUserId())).isTrue();

        Renter renter = renterRepository.findByUser_Id(saved.getUserId()).orElseThrow();
        assertThat(renter.getCccdNo()).isNull();
        assertThat(renter.getCccdImageUrl()).isNull();
        assertThat(renter.getCccdImageBackUrl()).isNull();
        assertThat(renter.getPortraitUrl()).isNull();
    }

    // ------------------------------------------------------------- duplicates

    @Test
    @DisplayName("duplicate email yields 409")
    void duplicateEmailConflicts() throws Exception {
        // send-otp itself now refuses a registered address, so the registration token has to be
        // minted directly: this test covers the register() guard, not the send-otp guard.
        String token = tokenService.issueRegistration(EXISTING_EMAIL);

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("send-otp fails fast for an already-registered email (409)")
    void sendOtpRejectsExistingEmail() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING_EMAIL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @DisplayName("a refused send-otp leaves no OTP behind, so verify-otp cannot proceed")
    void refusedSendOtpStoresNothing() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING_EMAIL))))
                .andExpect(status().isConflict());

        // No entry was stored: verification reports "expired", i.e. there is nothing to brute-force
        // and the client cannot walk away with a registration token.
        mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EXISTING_EMAIL, "otp", "123456"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("OTP has expired"));
    }

    // ------------------------------------------------------------------- OTP

    @Test
    @DisplayName("invalid OTP yields 400")
    void invalidOtp() throws Exception {
        String email = "wrongotp@ecomotion.vn";
        sendOtp(email);

        mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OTP"));
    }

    @Test
    @DisplayName("verifying without sending yields 410 (no entry)")
    void verifyBeforeSend() throws Exception {
        mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "ghost@ecomotion.vn", "otp", "123456"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    @DisplayName("too many wrong attempts yields 429")
    void tooManyAttempts() throws Exception {
        String email = "bruteforce@ecomotion.vn";
        sendOtp(email);

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post(VERIFY_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", "000000"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", "000000"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("immediate resend is blocked by the cooldown (429)")
    void resendCooldown() throws Exception {
        String email = "cooldown@ecomotion.vn";
        sendOtp(email);

        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Please wait before requesting a new OTP"));
    }

    // -------------------------------------------------------------- validation

    @Test
    @DisplayName("send-otp rejects a malformed email with 400")
    void sendOtpValidation() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("password shorter than 8 characters yields 400")
    void passwordTooShort() throws Exception {
        String token = verifiedToken("shortpw@ecomotion.vn");

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", "short",
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("DRIVER role is rejected at registration")
    void driverRoleRejected() throws Exception {
        String token = verifiedToken("driver@ecomotion.vn");

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "DRIVER",
                        "agreeTerms", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role is not allowed for registration"));
    }

    @Test
    @DisplayName("PROVIDER role is not available yet and is rejected")
    void providerRoleRejected() throws Exception {
        String token = verifiedToken("provider-attempt@ecomotion.vn");

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "PROVIDER",
                        "agreeTerms", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Role is not allowed for registration"));
    }

    @Test
    @DisplayName("ADMIN role is rejected at registration")
    void adminRoleRejected() throws Exception {
        String token = verifiedToken("admin-attempt@ecomotion.vn");

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "ADMIN",
                        "agreeTerms", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("rejecting the terms yields 400")
    void termsNotAccepted() throws Exception {
        String token = verifiedToken("noterms@ecomotion.vn");

        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a garbage registration token yields 400")
    void garbageTokenRejected() throws Exception {
        mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", "not-a-real-token",
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Registration token is invalid or expired"));
    }

    // -------------------------------------------------------------- security

    @Test
    @DisplayName("the stored password is BCrypt-hashed and never echoed back")
    void passwordIsHashedAndNotLeaked() throws Exception {
        String email = "hashed@ecomotion.vn";
        String token = verifiedToken(email);

        MvcResult result = mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isCreated())
                .andReturn();

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.toString()).doesNotContain("passwordHash").doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("no refresh cookie is issued at registration")
    void noRefreshCookieIssued() throws Exception {
        String token = verifiedToken("nocookie@ecomotion.vn");

        MvcResult result = mockMvc.perform(registerRequest(Map.of(
                        "registrationToken", token,
                        "password", PASSWORD,
                        "role", "RENTER",
                        "agreeTerms", true)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE)).isNull();
    }

    // ------------------------------------------------------------------- docs

    @Test
    @DisplayName("Swagger exposes the registration + activation endpoints")
    void openApiDocumentsRegistrationEndpoints() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode paths = objectMapper.readTree(apiDocs).at("/paths");
        assertThat(paths.has(SEND_PATH)).isTrue();
        assertThat(paths.has(VERIFY_PATH)).isTrue();
        assertThat(paths.has(REGISTER_PATH)).isTrue();
        assertThat(paths.has("/api/auth/activate/send-phone-otp")).isTrue();
        assertThat(paths.has("/api/auth/activate/verify-phone-otp")).isTrue();
        assertThat(paths.has("/api/auth/activate/status")).isTrue();
        assertThat(paths.has("/api/auth/activate/scan-cccd")).isTrue();
        assertThat(paths.has("/api/auth/activate/face-match")).isTrue();
        // There is no standalone "complete" step: the face match activates the account in place.
        assertThat(paths.has("/api/auth/activate/complete")).isFalse();
        assertThat(paths.has("/api/auth/activate/resume-otp")).isTrue();
        assertThat(paths.has("/api/auth/activate/resume")).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerRequest(
            Map<String, Object> payload) throws Exception {
        return post(REGISTER_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HashMap<>(payload)));
    }

    /** Runs send-otp and returns the echoed code. */
    private String sendOtp(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("otp").asText();
    }

    /** Sends + verifies the OTP, returning a registration token. */
    private String verifiedToken(String email) throws Exception {
        String code = sendOtp(email);
        MvcResult result = mockMvc.perform(post(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", code))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("registrationToken").asText();
    }
}
