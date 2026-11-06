package com.fptufa26.ecomotion.notification.internal.service;

import com.fptufa26.ecomotion.common.security.JwtService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F14: SSE chỉ xác thực bằng header Authorization. Token trong query string không còn được chấp nhận.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationStreamAuthTest {
    @LocalServerPort int port;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JwtService jwt;

    @Test
    void streamAcceptsAuthorizationHeader() throws Exception {
        String token = persistRenterToken();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(
                    HttpRequest.newBuilder(streamUri("/api/v1/notifications/stream"))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try {
                assertThat(response.statusCode()).isEqualTo(200);
            } finally {
                response.body().close();
            }
        }
    }

    @Test
    void tokenInQueryStringIsRejectedOnBothPaths() throws Exception {
        String token = persistRenterToken();
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[] {"/api/v1/notifications/stream", "/api/notifications/stream"}) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(streamUri(path + "?token=" + token)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(path).isEqualTo(401);
            }
        }
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(streamUri("/api/v1/notifications/stream"))
                            .header("Authorization", "Bearer not-a-real-jwt").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    private URI streamUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String persistRenterToken() {
        TransactionTemplate tx = new TransactionTemplate(manager);
        User user = tx.execute(s -> {
            User u = new User();
            u.setEmail(UUID.randomUUID() + "@stream-auth.test");
            u.setPasswordHash("test-only");
            u.setRole(Role.RENTER);
            u.setStatus(UserStatus.ACTIVE);
            em.persist(u);
            return u;
        });
        return jwt.generateAccessToken(user);
    }
}
