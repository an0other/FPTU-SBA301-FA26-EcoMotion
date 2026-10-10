package com.fptufa26.ecomotion.common.config;

import com.fptufa26.ecomotion.vehicle.enums.PlanType;
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
        assertThat(get("/api/auth/me").statusCode()).isEqualTo(401);
    }
}
