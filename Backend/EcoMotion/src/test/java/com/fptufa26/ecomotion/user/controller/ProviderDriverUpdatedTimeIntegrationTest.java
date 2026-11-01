package com.fptufa26.ecomotion.user.controller;

import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.service.ProviderDriverService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ProviderDriverUpdatedTimeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DriverRepository drivers;
    @Autowired ProviderDriverService service;
    @Autowired EntityManager entityManager;

    @Test
    void changingOnlyNamePersistsDriverTimestampAndListReadsLeaveItUnchanged() throws Exception {
        var driver = drivers.findAll().getFirst();
        Long driverId = driver.getId();
        Long providerUserId = driver.getProvider().getUser().getId();
        driver.getProvider().getUser().getPasswordHash();
        var principal = new SecurityUser(driver.getProvider().getUser());
        entityManager.flush();
        Instant old = Instant.parse("2000-01-01T00:00:00Z");
        entityManager.createNativeQuery("update driver set updated_at = :time where id = :id")
                .setParameter("time", old).setParameter("id", driverId).executeUpdate();
        entityManager.clear();
        driver = drivers.findById(driverId).orElseThrow();
        var response = service.updateDriver(providerUserId, driverId, new UpdateDriverByProviderRequest(
                "Updated name", driver.getUser().getPhone(), driver.getPortraitUrl(), driver.getLicenseNo(),
                driver.getLicenseClass(), driver.getLicenseExpiry(), driver.getLicenseImageUrl()));
        assertThat(response.updatedAt()).isAfter(old);
        entityManager.clear();
        Instant savedAt = drivers.findById(driverId).orElseThrow().getUpdatedAt();
        assertThat(savedAt).isCloseTo(response.updatedAt(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/provider/drivers").with(user(principal)).param("offset", "0")
                            .param("search", "Updated name").param("limit", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(driverId));
            entityManager.flush();
            entityManager.clear();
            assertThat(drivers.findById(driverId).orElseThrow().getUpdatedAt()).isEqualTo(savedAt);
        }
    }

    @Test
    void apiValidatesDatesAndPaginationAndReturnsEmptySearch() throws Exception {
        var principal = new SecurityUser(drivers.findAll().getFirst().getProvider().getUser());
        mvc.perform(get("/api/provider/drivers").with(user(principal)).param("offset", "0")
                        .param("updatedFrom", "2026-10-29").param("updatedTo", "2026-10-28"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/provider/drivers").with(user(principal)).param("offset", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/provider/drivers").with(user(principal)).param("offset", "0")
                        .param("updatedFrom", "invalid-date"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/provider/drivers").with(user(principal)).param("offset", "0")
                        .param("search", "no-such-driver-xyz"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.hasMore").value(false));
    }
}
