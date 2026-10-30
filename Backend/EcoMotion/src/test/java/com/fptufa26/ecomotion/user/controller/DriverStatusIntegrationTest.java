package com.fptufa26.ecomotion.user.controller;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class DriverStatusIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DriverRepository drivers;
    @Autowired com.fptufa26.ecomotion.user.repository.ProviderRepository providers;
    @Autowired BookingRepository bookings;
    @Test void ownReadinessAndProviderProjectionStayConsistent() throws Exception {
        Driver driver = drivers.findAll().stream().filter(d -> d.getStatus() == DriverStatus.APPROVED).findFirst().orElseThrow();
        // Isolate readiness from existing seeded schedule data.
        bookings.findAll().stream().filter(b -> b.getDriver() != null && b.getDriver().getId().equals(driver.getId())).forEach(b -> b.setDriver(null));
        bookings.flush();
        var principal = new SecurityUser(driver.getUser());
        for (DriverActivityStatus status : DriverActivityStatus.values()) {
            mvc.perform(put("/api/v1/driver/status").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"activityStatus\":\"" + status.name() + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.operationalStatus").value(status.name()));
            assertThat(drivers.findById(driver.getId()).orElseThrow().getActivityStatus()).isEqualTo(status);
            mvc.perform(get("/api/provider/drivers").with(user(new SecurityUser(driver.getProvider().getUser())))
                    .param("operationalStatus", status.name()).param("sort", "status_updated_at"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + driver.getId() + ")].operationalStatus").value(org.hamcrest.Matchers.contains(status.name())));
        }
    }
    @Test void providerCannotUpdateDriverReadinessAndDerivedStatusIsRejected() throws Exception {
        Driver driver = drivers.findAll().getFirst();
        mvc.perform(put("/api/v1/driver/status").with(user(new SecurityUser(driver.getProvider().getUser())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activityStatus\":\"AVAILABLE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/driver/status").with(user(new SecurityUser(driver.getUser())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activityStatus\":\"ON_TRIP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void providerCannotReadOrChangeAnotherProvidersDriver() throws Exception {
        Driver foreign = drivers.findAll().getFirst();
        var outsider = providers.findAll().stream().filter(p -> !p.getId().equals(foreign.getProvider().getId())).findFirst().orElseThrow();
        var principal = new SecurityUser(outsider.getUser());
        mvc.perform(get("/api/provider/drivers/{id}", foreign.getId()).with(user(principal))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/provider/drivers/{id}/status", foreign.getId()).with(user(principal))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound());
    }
    @Test void activeTripCannotBeOverriddenThroughApi() throws Exception {
        var booking = bookings.findAll().stream().filter(b -> b.getStatus() == com.fptufa26.ecomotion.booking.enums.BookingStatus.IN_PROGRESS && b.getDriver() != null).findFirst().orElseThrow();
        var principal = new SecurityUser(booking.getDriver().getUser());
        mvc.perform(put("/api/v1/driver/status").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"activityStatus\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/driver/status").with(user(principal)).param("driverId", "-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.driverId").value(booking.getDriver().getId()))
                .andExpect(jsonPath("$.data.operationalStatus").value("ON_TRIP"));
    }
}
