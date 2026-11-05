package com.fptufa26.ecomotion.common.service;

import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(NotificationStreamHttpTest.Config.class)
class NotificationStreamHttpTest {
    @LocalServerPort int port;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JwtService jwt;

    @TestConfiguration static class Config {
        @Bean Probe probe(NotificationStreamService streams, PlatformTransactionManager manager, EntityManager em) {
            return new Probe(streams, new TransactionTemplate(manager), em);
        }
    }
    @RestController static class Probe {
        final NotificationStreamService streams; final TransactionTemplate tx; final EntityManager em;
        Probe(NotificationStreamService streams, TransactionTemplate tx, EntityManager em) {this.streams=streams;this.tx=tx;this.em=em;}
        @GetMapping(value="/api/renter/sse-test/timeout/{id}",produces="text/event-stream")
        SseEmitter timeout(@PathVariable Long id) throws Exception {
            // Deliberately leave a timed-out emitter registered: reproduce the original container failure.
            SseEmitter emitter=new SseEmitter(50L); streams.registerEmitter(id,emitter);
            emitter.send(SseEmitter.event().name("INIT").data("connected")); return emitter;
        }
        @PostMapping("/api/renter/sse-test/commit/{id}") String commit(@PathVariable Long id) {
            tx.executeWithoutResult(s -> {
                em.find(User.class,id).setFullName("committed");
                streams.sendEventToUser(id,"BOOKING_CHANGED",Map.of("committed",true));
            });
            return "committed";
        }
    }
    @Test void timedOutTomcatStreamCannotFailCommittedHttpRequest() throws Exception {
        TransactionTemplate tx=new TransactionTemplate(manager);
        User user=tx.execute(s -> {
            User u=new User();u.setEmail(UUID.randomUUID()+"@stream.test");u.setPasswordHash("test-only");
            u.setRole(Role.RENTER);u.setStatus(UserStatus.ACTIVE);em.persist(u);return u;
        });
        String token=jwt.generateAccessToken(user);
        try (HttpClient client=HttpClient.newHttpClient()) {
            HttpResponse<java.io.InputStream> stream=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/renter/sse-test/timeout/"+user.getId()))
                    .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            assertThat(stream.statusCode()).isEqualTo(200);
            // EOF proves the actual embedded Tomcat AsyncContext has timed out.
            try { stream.body().readAllBytes(); }
            catch (java.io.IOException disconnected) { /* Tomcat may abort chunked transfer on timeout. */ }
            finally { stream.body().close(); }
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/renter/sse-test/commit/"+user.getId()))
                    .header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            String persistedName=tx.execute(s -> em.find(User.class,user.getId()).getFullName());
            assertThat(persistedName).isEqualTo("committed");
        }
    }
}
