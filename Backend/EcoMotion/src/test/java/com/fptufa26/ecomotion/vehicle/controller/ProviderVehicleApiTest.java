package com.fptufa26.ecomotion.vehicle.controller;

import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ProviderVehicleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @DisplayName("unauthenticated caller cannot access provider vehicles")
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/provider/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("renter role is forbidden from provider vehicles")
    void renterRoleReturns403() throws Exception {
        User renter = userRepository.findByEmailIgnoreCase("renter01@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(renter);

        mockMvc.perform(get("/api/provider/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("provider can list own vehicles with stats")
    void providerCanListVehicles() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);

        mockMvc.perform(get("/api/provider/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.stats.total").isNumber())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("provider can toggle visibility of own approved vehicle")
    void providerCanToggleVisibility() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);
        Provider provider = userService.findProviderByUserId(providerUser.getUserId()).orElseThrow();

        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).stream()
                .filter(v -> v.getStatus() == VehicleStatus.APPROVED)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(patch("/api/provider/vehicles/" + vehicle.getId() + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(patch("/api/provider/vehicles/" + vehicle.getId() + "/visibility")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("provider can register new vehicle with 200 SUCCESS and PENDING status")
    void providerCanRegisterNewVehicle() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        int randomDigits = 10000 + new Random().nextInt(90000);
        String validPlate = "51K-" + randomDigits;

        String payload = """
                {
                    "chassisNo": "VIN-%s",
                    "engineNo": "ENG-%s",
                    "licensePlate": "%s",
                    "brand": "VinFast",
                    "model": "VF 8 Plus",
                    "manufactureYear": 2024,
                    "seatCount": 5,
                    "transmission": "AUTOMATIC",
                    "fuelType": "EV",
                    "color": "Xanh Lục Bảo",
                    "maintenanceBufferMinutes": 60,
                    "coverImageUrl": "https://example.com/vf8.jpg"
                }
                """.formatted(uniqueSuffix, uniqueSuffix, validPlate);

        mockMvc.perform(post("/api/provider/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.brand").value("VinFast"))
                .andExpect(jsonPath("$.data.model").value("VF 8 Plus"))
                .andExpect(jsonPath("$.data.coverImageUrl").value("https://example.com/vf8.jpg"));
    }

    @Test
    @DisplayName("provider registration is rejected with 400 Bad Request when license plate pattern is invalid like 77AAAAAAA")
    void providerCannotRegisterVehicleWithInvalidLicensePlate() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        String payload = """
                {
                    "chassisNo": "VIN-%s",
                    "engineNo": "ENG-%s",
                    "licensePlate": "77AAAAAAA",
                    "brand": "VinFast",
                    "model": "VF 8 Plus",
                    "manufactureYear": 2024,
                    "seatCount": 5,
                    "transmission": "AUTOMATIC",
                    "fuelType": "EV",
                    "color": "Xanh Lục Bảo",
                    "maintenanceBufferMinutes": 60,
                    "coverImageUrl": "https://example.com/vf8.jpg"
                }
                """.formatted(uniqueSuffix, uniqueSuffix);

        mockMvc.perform(post("/api/provider/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("provider can configure a vehicle deposit policy")
    void providerCanConfigureVehicleDepositPolicy() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);
        Provider provider = userService.findProviderByUserId(providerUser.getUserId()).orElseThrow();
        Vehicle vehicle = vehicleRepository.findByProvider_IdOrderByIdAsc(provider.getId()).getFirst();

        mockMvc.perform(patch("/api/provider/vehicles/{id}/deposit-policy", vehicle.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "depositRatioOverride": 0.5,
                                  "minimumDepositAmount": 5000000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.depositRatioOverride").value(0.5))
                .andExpect(jsonPath("$.data.minimumDepositAmount").value(5000000))
                .andExpect(jsonPath("$.data.effectiveRatio").value(0.5));
    }

    @Test
    @DisplayName("provider can enter a plate with spaces and separators")
    void providerCanRegisterVehicleWithFriendlyPlateInput() throws Exception {
        User providerUser = userRepository.findByEmailIgnoreCase("provider.a@ecomotion.vn").orElseThrow();
        String token = jwtService.generateAccessToken(providerUser);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        int randomDigits = 10000 + new Random().nextInt(90000);
        String plate = "51k " + randomDigits;

        String payload = """
                {
                    "chassisNo": "VIN-%s",
                    "engineNo": "ENG-%s",
                    "licensePlate": "%s",
                    "brand": "VinFast",
                    "model": "VF 8 Plus",
                    "manufactureYear": 2024,
                    "seatCount": 5,
                    "transmission": "AUTOMATIC",
                    "fuelType": "EV",
                    "color": "Xanh Lục Bảo",
                    "maintenanceBufferMinutes": 60
                }
                """.formatted(uniqueSuffix, uniqueSuffix, plate);

        mockMvc.perform(post("/api/provider/vehicles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.licensePlate").value("51K-" + randomDigits));
    }
}
