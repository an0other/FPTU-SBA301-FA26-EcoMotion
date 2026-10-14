package com.fptufa26.ecomotion.common.config;

import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class StaticImageAccessTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private VehicleImageRepository vehicleImageRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    @DisplayName("a vehicle photo is served with 200 and without authentication")
    void imagesArePubliclyReadable() throws Exception {
        assertThat(get("/images/2/2/rear-left-view-121.webp")).isEqualTo(200);
    }

    @Test
    @DisplayName("the folder-id layout still lines up with the seeded vehicle ids")
    void secondProvider_folderIsServed() throws Exception {
        assertThat(get("/images/4/5/BMW-320i-M-Sport-830x415.webp")).isEqualTo(200);
    }

    @Test
    @DisplayName("every seeded vehicle photo resolves over HTTP")
    void everySeededPhotoIsServed() throws Exception {
        List<VehicleImage> images = vehicleImageRepository.findAll();
        assertThat(images).as("seeder must have written vehicle_image rows").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (VehicleImage image : images) {
            int status = get(image.getImageUrl());
            if (status != 200) {
                failures.add(status + " " + image.getImageUrl());
            }
        }
        assertThat(failures).as("image URLs that did not return 200").isEmpty();
    }
}
