package com.fptufa26.ecomotion.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the public landing page contract at the web layer.
 *
 * <p>The renter home page ({@code /} in the SPA) is the car list, and it is reachable before anyone
 * signs in. That only works if {@code GET /api/vehicles} answers anonymously - and because
 * {@code anyRequest().authenticated()} is the catch-all, forgetting the {@code permitAll} rule turns
 * the home page into a 401 with no cars on it.
 *
 * <p>These tests run against a real embedded Tomcat ({@code RANDOM_PORT}) rather than MockMvc so the
 * whole filter chain, including {@code JwtAuthenticationFilter}, participates exactly as it does in
 * a browser. No {@code Authorization} header is ever sent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PublicVehicleListAccessTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("an anonymous visitor can load the home page fleet")
    void anonymousCallerCanListVehicles() throws Exception {
        HttpResponse<String> response = get("/api/vehicles");

        assertThat(response.statusCode()).isEqualTo(200);
        // Not just "a 200 with an empty envelope": the seeded fleet has to actually come through.
        assertThat(response.body()).contains("\"items\"").contains("\"hasMore\"").contains("BMW");
    }

    @Test
    @DisplayName("anonymous filtering and paging work too, not just the bare list")
    void anonymousCallerCanFilterAndPage() throws Exception {
        HttpResponse<String> response = get("/api/vehicles?planType=HOURLY&limit=2&offset=0");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"limit\":2");
    }

    @Test
    @DisplayName("only the fleet read was opened - the account endpoint still rejects anonymous callers")
    void accountEndpointStaysProtected() throws Exception {
        // Without this the previous test would still pass if someone widened the rule to
        // permitAll("/**"), which is the failure mode worth catching.
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
    }
}
