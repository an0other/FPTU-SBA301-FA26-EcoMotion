package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminApiIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private com.fptufa26.ecomotion.user.repository.UserRepository userRepository;

        private String getAdminToken() {
                return jwtService.generateAccessToken(userRepository.findById(3L).orElseThrow());
        }

        private String getRenterToken() {
                return jwtService.generateAccessToken(userRepository.findById(1L).orElseThrow());
        }

        @Test
        @DisplayName("A01: Dashboard stats returns 200 for ADMIN, 401 for anonymous, 403 for RENTER")
        void testA01DashboardStatsSecurity() throws Exception {
                // Anonymous -> 401
                mockMvc.perform(get("/api/admin/dashboard/stats"))
                                .andExpect(status().isUnauthorized());

                // Renter -> 403
                mockMvc.perform(get("/api/admin/dashboard/stats")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getRenterToken()))
                                .andExpect(status().isForbidden());

                // Admin -> 200
                mockMvc.perform(get("/api/admin/dashboard/stats")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAdminToken()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data.totalVehicles").isNumber())
                                .andExpect(jsonPath("$.data.activeUsers").isNumber());
        }

        @Test
        @DisplayName("A02: List users, providers, drivers, ekyc returns 200 for ADMIN")
        void testA02UsersEndpoints() throws Exception {
                String token = "Bearer " + getAdminToken();

                mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());

                mockMvc.perform(get("/api/admin/providers").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());

                mockMvc.perform(get("/api/admin/drivers").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());

                mockMvc.perform(get("/api/admin/ekyc").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("A03: List admin vehicles returns 200 for ADMIN")
        void testA03VehiclesEndpoint() throws Exception {
                String token = "Bearer " + getAdminToken();

                mockMvc.perform(get("/api/admin/vehicles").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("A04: List operations bookings, disputes, and payments returns 200 for ADMIN")
        void testA04OperationsEndpoints() throws Exception {
                String token = "Bearer " + getAdminToken();

                mockMvc.perform(get("/api/admin/operations/bookings").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());

                mockMvc.perform(get("/api/admin/operations/disputes").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());

                mockMvc.perform(get("/api/admin/operations/payments").header(HttpHeaders.AUTHORIZATION, token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value("SUCCESS"))
                                .andExpect(jsonPath("$.data").isArray());
        }
}
