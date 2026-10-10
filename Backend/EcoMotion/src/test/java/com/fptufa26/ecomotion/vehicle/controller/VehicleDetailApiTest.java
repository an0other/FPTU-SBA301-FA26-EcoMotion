package com.fptufa26.ecomotion.vehicle.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class VehicleDetailApiTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("an anonymous visitor can open a single approved vehicle by id")
    void anonymousCallerCanOpenDetail() throws Exception {
        HttpResponse<String> response = get("/api/vehicles/1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"brand\":\"VinFast\"")
                .contains("\"model\":\"VF 6\"")
                .contains("\"imageUrls\"")
                .contains("\"plans\"");
    }

    @Test
    @DisplayName("the detail payload carries no plate, chassis or engine identifier")
    void detailPayloadHasNoSensitiveIdentifiers() throws Exception {
        HttpResponse<String> response = get("/api/vehicles/1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .doesNotContain("licensePlate")
                .doesNotContain("chassisNo")
                .doesNotContain("engineNo")
                .doesNotContain("51A-11111");
    }

    @Test
    @DisplayName("the list payload no longer carries the plate either")
    void listPayloadHasNoPlate() throws Exception {
        HttpResponse<String> response = get("/api/vehicles");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("licensePlate").doesNotContain("51A-");
    }

    @Test
    @DisplayName("a plate keyword still resolves server-side without being echoed back")
    void plateKeywordStillMatches() throws Exception {
        HttpResponse<String> response = get("/api/vehicles?keyword=51A-11111");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"total\":1").doesNotContain("51A-11111");
    }

    @Test
    @DisplayName("an unknown vehicle id answers 404")
    void unknownIdAnswersNotFound() throws Exception {
        HttpResponse<String> response = get("/api/vehicles/999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("NOT_FOUND");
    }
}
