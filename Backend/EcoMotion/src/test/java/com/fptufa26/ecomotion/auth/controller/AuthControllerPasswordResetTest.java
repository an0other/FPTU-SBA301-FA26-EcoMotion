package com.fptufa26.ecomotion.auth.controller;

import com.fptufa26.ecomotion.auth.service.PasswordResetService;
import com.fptufa26.ecomotion.auth.service.AuthService;
import com.fptufa26.ecomotion.auth.service.RegistrationService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerPasswordResetTest {
    @Test void validatesInputsAndReturnsGenericSendResponse() throws Exception {
        var service = mock(PasswordResetService.class);
        var controller = new AuthController(mock(AuthService.class), mock(RegistrationService.class),
                service, Duration.ofSeconds(60), "refresh_token", "/api/auth", false, "Lax", Duration.ofDays(7));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"renter@example.com\",\"otp\":\"123456\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"renter@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resendCooldownSeconds").value(60))
                .andExpect(jsonPath("$.otp").doesNotExist());
        verify(service).sendOtp("renter@example.com");
    }
}
