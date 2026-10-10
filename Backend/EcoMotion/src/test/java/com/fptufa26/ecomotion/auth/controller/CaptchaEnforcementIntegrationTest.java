package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.captcha.enabled=true",
        "app.captcha.secret-key=test-secret",
        "app.captcha.min-score=0.5"
})
class CaptchaEnforcementIntegrationTest {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("login without a captcha token yields 403")
    void loginWithoutCaptchaTokenRejected() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "someone@ecomotion.vn",
                                "password", "P@ssw0rd",
                                "rememberMe", false))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Captcha verification failed"));
    }

    @Test
    @DisplayName("login with a blank captcha token yields 403")
    void loginWithBlankCaptchaTokenRejected() throws Exception {
        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "someone@ecomotion.vn",
                                "password", "P@ssw0rd",
                                "captchaToken", "   ",
                                "rememberMe", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Captcha verification failed"));
    }

    @Test
    @DisplayName("register without a captcha token yields 403 before any DB write")
    void registerWithoutCaptchaTokenRejected() throws Exception {
        mockMvc.perform(post(REGISTER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationToken", "whatever",
                                "password", "P@ssw0rd123",
                                "fullName", "Nguyen Van A",
                                "phone", "0900000001",
                                "role", "RENTER",
                                "agreeTerms", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Captcha verification failed"));
    }
}
