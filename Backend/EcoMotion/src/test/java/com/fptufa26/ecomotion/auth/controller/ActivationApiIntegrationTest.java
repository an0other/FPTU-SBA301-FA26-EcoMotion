package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.repository.RefreshTokenRepository;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.otp.provider=logging",
        "app.otp.expose-in-response=true",
        "app.ekyc.provider=mock",
        "app.ekyc.mock.force-fail=false",
        "app.phone-otp.resend-cooldown=60s",
        "app.upload.dir=target/test-uploads"
})
class ActivationApiIntegrationTest {

    private static final String PASSWORD = "P@ssw0rd123";
    private static final String SEND_REGISTER_OTP = "/api/auth/register/send-otp";
    private static final String VERIFY_REGISTER_OTP = "/api/auth/register/verify-otp";
    private static final String REGISTER = "/api/auth/register";
    private static final String SEND_PHONE_OTP = "/api/auth/activate/send-phone-otp";
    private static final String VERIFY_PHONE_OTP = "/api/auth/activate/verify-phone-otp";
    private static final String STATUS = "/api/auth/activate/status";
    private static final String SCAN_CCCD = "/api/auth/activate/scan-cccd";
    private static final String FACE_MATCH = "/api/auth/activate/face-match";
    private static final String RESUME_OTP = "/api/auth/activate/resume-otp";
    private static final String RESUME = "/api/auth/activate/resume";

    private static final String EXISTING_PHONE = "0909999999";
    private static final String MOCK_CCCD_NO = "079123456789";
    private static final String MOCK_FULL_NAME = "NGUYỄN VĂN A";
    private static final String MOCK_ADDRESS = "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RenterRepository renterRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        refreshTokenRepository.deleteAll();
        renterRepository.deleteAll();
        userRepository.deleteAll();

        User existing = new User();
        existing.setEmail("taken@ecomotion.vn");
        existing.setPhone(EXISTING_PHONE);
        existing.setFullName("Existing User");
        existing.setPasswordHash("$2a$04$abcdefghijklmnopqrstuv");
        existing.setRole(Role.RENTER);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setEkycStatus(EkycStatus.VERIFIED);
        userRepository.save(existing);
    }


    @Test
    @DisplayName("full activation: phone OTP → CCCD scan → face match activates the account in place")
    void fullActivationHappyPath() throws Exception {
        String email = "activate-me@ecomotion.vn";
        String activationToken = registerRenter(email);

        String phone = "0901234567";
        String otp = sendPhoneOtp(activationToken, phone);
        verifyPhone(activationToken, otp);

        scanCccd(activationToken);
        faceMatch(activationToken);

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEkycStatus()).isEqualTo(EkycStatus.VERIFIED);
        assertThat(saved.getPhone()).isEqualTo(phone);
        assertThat(saved.getFullName()).isEqualTo(MOCK_FULL_NAME);

        Renter renter = renterRepository.findByUser_Id(saved.getUserId()).orElseThrow();
        assertThat(renter.getCccdNo()).isEqualTo(MOCK_CCCD_NO);
        assertThat(renter.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(renter.getGender()).isEqualTo("Nam");
        assertThat(renter.getAddress()).isNotBlank();
        assertThat(renter.getCccdImageUrl()).isNotBlank();
        assertThat(renter.getPortraitUrl()).isNotBlank();

        Path front = Paths.get(uploadDir, renter.getCccdImageUrl());
        Path portrait = Paths.get(uploadDir, renter.getPortraitUrl());
        assertThat(Files.exists(front)).isTrue();
        assertThat(Files.exists(portrait)).isTrue();

        String rawPhone = jdbcTemplate.queryForObject(
                "select phone from users where email = ?", String.class, email);
        String rawCccd = jdbcTemplate.queryForObject(
                "select cccd_no from renter where user_id = ?", String.class, saved.getUserId());
        assertThat(rawPhone).isNotBlank().isNotEqualTo(phone);
        assertThat(rawCccd).isNotBlank().isNotEqualTo(MOCK_CCCD_NO);
    }

    @Test
    @DisplayName("the phone step persists users.phone immediately, before the remaining steps")
    void phoneStepPersistsImmediately() throws Exception {
        String email = "phone-first@ecomotion.vn";
        String activationToken = registerRenter(email);

        String otp = sendPhoneOtp(activationToken, "0901234601");
        verifyPhone(activationToken, otp);

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getPhone()).isEqualTo("0901234601");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.getEkycStatus()).isEqualTo(EkycStatus.UNVERIFIED);
    }


    @Test
    @DisplayName("stored names are fixed per role, so colliding upload names cannot overwrite each other")
    void storedNamesAreFixedPerRole() throws Exception {
        String email = "collision@ecomotion.vn";
        String activationToken = registerRenter(email);

        String phone = "0901234777";
        verifyPhone(activationToken, sendPhoneOtp(activationToken, phone));

        mockMvc.perform(multipart(SCAN_CCCD)
                        .file(image("cccdFront", "IMG_2024.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cccdFrontUrl").value(org.hamcrest.Matchers.endsWith("cccd-front.jpg")));

        mockMvc.perform(multipart(FACE_MATCH)
                        .file(image("portrait", "IMG_2024.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portraitUrl").value(org.hamcrest.Matchers.endsWith("portrait.jpg")));

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        Renter renter = renterRepository.findByUser_Id(saved.getUserId()).orElseThrow();

        assertThat(renter.getCccdImageUrl()).startsWith("renter/").endsWith("/cccd-front.jpg");
        assertThat(renter.getPortraitUrl()).startsWith("renter/").endsWith("/portrait.jpg");
        assertThat(renter.getCccdImageUrl()).isNotEqualTo(renter.getPortraitUrl());

        assertThat(Files.exists(Paths.get(uploadDir, renter.getCccdImageUrl()))).isTrue();
        assertThat(Files.exists(Paths.get(uploadDir, renter.getPortraitUrl()))).isTrue();
    }

    @Test
    @DisplayName("scanning the CCCD returns the OCR fields and persists them on the renter profile")
    void scanCccdPersistsOcrFields() throws Exception {
        String email = "scan@ecomotion.vn";
        String activationToken = registerRenter(email);

        MvcResult result = mockMvc.perform(multipart(SCAN_CCCD)
                        .file(image("cccdFront", "front.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(true))
                .andExpect(jsonPath("$.fullName").value(MOCK_FULL_NAME))
                .andExpect(jsonPath("$.cccdNo").value(MOCK_CCCD_NO))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-01-01"))
                .andExpect(jsonPath("$.gender").value("Nam"))
                .andExpect(jsonPath("$.address").value(MOCK_ADDRESS))
                .andExpect(jsonPath("$.cccdFrontUrl").isNotEmpty())
                .andReturn();

        assertThat(json(result).get("message").asText()).isNotBlank();

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getFullName()).isEqualTo(MOCK_FULL_NAME);
        assertThat(saved.getEkycStatus()).isEqualTo(EkycStatus.PENDING);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);

        Renter renter = renterRepository.findByUser_Id(saved.getUserId()).orElseThrow();
        assertThat(renter.getCccdNo()).isEqualTo(MOCK_CCCD_NO);
        assertThat(renter.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(renter.getGender()).isEqualTo("Nam");
        assertThat(renter.getAddress()).isEqualTo(MOCK_ADDRESS);
        assertThat(renter.getPortraitUrl()).isNull();
    }

    @Test
    @DisplayName("re-scanning the same CCCD is idempotent and does not trip the duplicate guard")
    void rescanningTheSameCccdIsAllowed() throws Exception {
        String activationToken = registerRenter("rescan@ecomotion.vn");

        scanCccd(activationToken);
        scanCccd(activationToken);

        User saved = userRepository.findByEmailIgnoreCase("rescan@ecomotion.vn").orElseThrow();
        Renter renter = renterRepository.findByUser_Id(saved.getUserId()).orElseThrow();
        assertThat(renter.getCccdNo()).isEqualTo(MOCK_CCCD_NO);
    }

    @Test
    @DisplayName("a CCCD already bound to another account yields 409")
    void duplicateCccdConflicts() throws Exception {
        String firstToken = registerRenter("cccd-owner@ecomotion.vn");
        scanCccd(firstToken);

        String secondToken = registerRenter("cccd-thief@ecomotion.vn");
        mockMvc.perform(multipart(SCAN_CCCD)
                        .file(image("cccdFront", "front.jpg"))
                        .param("activationToken", secondToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("This CCCD number is already registered to another account"));

        User second = userRepository.findByEmailIgnoreCase("cccd-thief@ecomotion.vn").orElseThrow();
        assertThat(second.getFullName()).isNull();
        assertThat(second.getEkycStatus()).isEqualTo(EkycStatus.UNVERIFIED);
        Renter secondRenter = renterRepository.findByUser_Id(second.getUserId()).orElseThrow();
        assertThat(secondRenter.getCccdNo()).isNull();
        assertThat(secondRenter.getCccdImageUrl()).isNull();
    }


    @Test
    @DisplayName("face match before scanning the CCCD yields 422")
    void faceMatchBeforeScan() throws Exception {
        String activationToken = registerRenter("face-early@ecomotion.vn");
        String otp = sendPhoneOtp(activationToken, "0901234603");
        verifyPhone(activationToken, otp);

        mockMvc.perform(multipart(FACE_MATCH)
                        .file(image("portrait", "portrait.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Vui lòng quét CCCD trước khi xác thực khuôn mặt."));
    }

    @Test
    @DisplayName("face match before the phone is verified yields 409 and leaves the account PENDING")
    void faceMatchBeforePhoneVerification() throws Exception {
        String email = "face-nophone@ecomotion.vn";
        String activationToken = registerRenter(email);
        scanCccd(activationToken);

        mockMvc.perform(multipart(FACE_MATCH)
                        .file(image("portrait", "portrait.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(renterRepository.findByUser_Id(saved.getUserId()).orElseThrow().getPortraitUrl()).isNull();
    }

    @Test
    @DisplayName("the activation steps are refused once the account is ACTIVE")
    void stepsRefusedOnceActive() throws Exception {
        String activationToken = registerRenter("already-active@ecomotion.vn");
        String otp = sendPhoneOtp(activationToken, "0901234602");
        verifyPhone(activationToken, otp);
        scanCccd(activationToken);
        faceMatch(activationToken);

        mockMvc.perform(multipart(FACE_MATCH)
                        .file(image("portrait", "portrait.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account is already active"));
    }

    @Test
    @DisplayName("an invalid activation token yields 400")
    void invalidActivationToken() throws Exception {
        mockMvc.perform(post(SEND_PHONE_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activationToken", "not-a-real-token",
                                "phone", "0901234567"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Activation token is invalid or has expired"));
    }

    @Test
    @DisplayName("a phone number already in use yields 409")
    void duplicatePhoneConflicts() throws Exception {
        String activationToken = registerRenter("dup-phone@ecomotion.vn");

        mockMvc.perform(post(SEND_PHONE_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activationToken", activationToken,
                                "phone", EXISTING_PHONE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a wrong phone OTP yields 400")
    void wrongPhoneOtp() throws Exception {
        String activationToken = registerRenter("wrong-phone-otp@ecomotion.vn");
        sendPhoneOtp(activationToken, "0901234568");

        mockMvc.perform(post(VERIFY_PHONE_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activationToken", activationToken,
                                "otp", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OTP"));
    }

    @Test
    @DisplayName("a non-image upload yields 400")
    void nonImageUploadRejected() throws Exception {
        String activationToken = registerRenter("bad-file@ecomotion.vn");

        MockMultipartFile pdf = new MockMultipartFile("cccdFront", "doc.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(multipart(SCAN_CCCD)
                        .file(pdf)
                        .param("activationToken", activationToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }


    @Test
    @DisplayName("status reports the current activation progress and drives which step is shown")
    void statusReportsProgress() throws Exception {
        String activationToken = registerRenter("status@ecomotion.vn");

        assertStatus(activationToken, "PENDING", "UNVERIFIED", false, false);

        String otp = sendPhoneOtp(activationToken, "0901234577");
        verifyPhone(activationToken, otp);
        assertStatus(activationToken, "PENDING", "UNVERIFIED", true, false);

        scanCccd(activationToken);
        assertStatus(activationToken, "PENDING", "PENDING", true, true);

        faceMatch(activationToken);
        assertStatus(activationToken, "ACTIVE", "VERIFIED", true, true);
    }

    @Test
    @DisplayName("status echoes the scanned identity fields so the page can re-hydrate them")
    void statusEchoesScannedIdentityFields() throws Exception {
        String activationToken = registerRenter("status-echo@ecomotion.vn");

        mockMvc.perform(post(STATUS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activationToken", activationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cccdScanned").value(false));

        scanCccd(activationToken);

        mockMvc.perform(post(STATUS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activationToken", activationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cccdScanned").value(true))
                .andExpect(jsonPath("$.fullName").value(MOCK_FULL_NAME))
                .andExpect(jsonPath("$.cccdNo").value(MOCK_CCCD_NO))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-01-01"))
                .andExpect(jsonPath("$.gender").value("Nam"))
                .andExpect(jsonPath("$.address").value(MOCK_ADDRESS));
    }


    @Test
    @DisplayName("resume: a PENDING account can be activated later via email OTP")
    void resumeActivationLater() throws Exception {
        String email = "resume-later@ecomotion.vn";
        registerRenter(email);

        String otp = resumeOtp(email);
        MvcResult resume = mockMvc.perform(post(RESUME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", otp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.activationToken").isNotEmpty())
                .andReturn();
        String activationToken = json(resume).get("activationToken").asText();

        String phoneOtp = sendPhoneOtp(activationToken, "0901234611");
        verifyPhone(activationToken, phoneOtp);
        scanCccd(activationToken);
        faceMatch(activationToken);

        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getEkycStatus()).isEqualTo(EkycStatus.VERIFIED);
    }

    @Test
    @DisplayName("resume picks up from the first outstanding step, not from scratch")
    void resumePicksUpFromTheOutstandingStep() throws Exception {
        String email = "resume-midway@ecomotion.vn";
        String firstToken = registerRenter(email);

        String otp = sendPhoneOtp(firstToken, "0901234612");
        verifyPhone(firstToken, otp);

        String resumeCode = resumeOtp(email);
        MvcResult resume = mockMvc.perform(post(RESUME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", resumeCode))))
                .andExpect(status().isOk())
                .andReturn();
        String secondToken = json(resume).get("activationToken").asText();

        assertStatus(secondToken, "PENDING", "UNVERIFIED", true, false);

        scanCccd(secondToken);
        faceMatch(secondToken);
        assertStatus(secondToken, "ACTIVE", "VERIFIED", true, true);
    }

    @Test
    @DisplayName("resume with an unknown email yields 404")
    void resumeUnknownEmail() throws Exception {
        mockMvc.perform(post(RESUME_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ghost@ecomotion.vn"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No pending activation found for this email"));
    }

    @Test
    @DisplayName("resume for an already-active account yields 409")
    void resumeAlreadyActiveAccount() throws Exception {
        mockMvc.perform(post(RESUME_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "taken@ecomotion.vn"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account is already active"));
    }

    @Test
    @DisplayName("resume with a wrong OTP yields 400")
    void resumeWrongOtp() throws Exception {
        String email = "resume-wrong-otp@ecomotion.vn";
        registerRenter(email);
        resumeOtp(email);

        mockMvc.perform(post(RESUME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OTP"));
    }


    private String resumeOtp(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(RESUME_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("otp").asText();
    }

    private void assertStatus(
            String activationToken, String status, String ekycStatus, boolean phone, boolean cccd) throws Exception {
        mockMvc.perform(post(STATUS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("activationToken", activationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.ekycStatus").value(ekycStatus))
                .andExpect(jsonPath("$.phoneVerified").value(phone))
                .andExpect(jsonPath("$.cccdScanned").value(cccd));
    }

    private void scanCccd(String activationToken) throws Exception {
        mockMvc.perform(multipart(SCAN_CCCD)
                        .file(image("cccdFront", "front.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(true));
    }

    private void faceMatch(String activationToken) throws Exception {
        mockMvc.perform(multipart(FACE_MATCH)
                        .file(image("portrait", "portrait.jpg"))
                        .param("activationToken", activationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.activated").value(true))
                .andExpect(jsonPath("$.portraitUrl").isNotEmpty());
    }

    private MockMultipartFile image(String field, String filename) {
        return new MockMultipartFile(field, filename, MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());
    }

    private String registerRenter(String email) throws Exception {
        MvcResult send = mockMvc.perform(post(SEND_REGISTER_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk())
                .andReturn();
        String code = json(send).get("otp").asText();

        MvcResult verify = mockMvc.perform(post(VERIFY_REGISTER_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "otp", code))))
                .andExpect(status().isOk())
                .andReturn();
        String registrationToken = json(verify).get("registrationToken").asText();

        MvcResult register = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationToken", registrationToken,
                                "password", PASSWORD,
                                "role", "RENTER",
                                "agreeTerms", true))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(register).get("activationToken").asText();
    }

    private String sendPhoneOtp(String activationToken, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post(SEND_PHONE_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activationToken", activationToken,
                                "phone", phone))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("otp").asText();
    }

    private void verifyPhone(String activationToken, String otp) throws Exception {
        mockMvc.perform(post(VERIFY_PHONE_OTP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activationToken", activationToken,
                                "otp", otp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneVerified").value(true));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
