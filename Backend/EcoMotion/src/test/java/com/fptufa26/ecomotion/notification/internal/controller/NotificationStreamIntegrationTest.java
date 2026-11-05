package com.fptufa26.ecomotion.notification.internal.controller;

import com.fptufa26.ecomotion.common.security.SecurityUser;
import com.fptufa26.ecomotion.notification.internal.service.NotificationStreamService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationStreamIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired NotificationStreamService streams;

    @Test
    void authenticatedStreamsKeepBothUrlsAndRouteEventsToTheCorrectUser() throws Exception {
        User firstUser = new User();
        firstUser.setId(900001L);
        firstUser.setRole(Role.RENTER);
        firstUser.setEmail("first@example.test");
        User secondUser = new User();
        secondUser.setId(900002L);
        secondUser.setRole(Role.RENTER);
        secondUser.setEmail("second@example.test");
        try {
            var first = mvc.perform(get("/api/v1/notifications/stream").with(user(new SecurityUser(firstUser))))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted()).andReturn();
            var second = mvc.perform(get("/api/notifications/stream").with(user(new SecurityUser(secondUser))))
                    .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
            assertThat(first.getResponse().getContentAsString()).contains("event:INIT", "900001");
            assertThat(second.getResponse().getContentAsString()).contains("event:INIT", "900002");
            streams.sendEventToUser(900001L, "TEST_EVENT", "message-for-first");
            assertThat(first.getResponse().getContentAsString()).contains("event:TEST_EVENT", "message-for-first");
            assertThat(second.getResponse().getContentAsString()).doesNotContain("message-for-first");
        } finally {
            streams.removeEmitter(900001L);
            streams.removeEmitter(900002L);
        }
    }

    @Test
    void anonymousClientsCannotOpenEitherStream() throws Exception {
        mvc.perform(get("/api/v1/notifications/stream")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }
}
