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

/**
 * Guards the static-image wiring end to end at the web layer.
 *
 * <p>Two independent things have to hold for a photo to reach the browser, and both have silently
 * broken before: the resource handler must resolve {@code app.images.root} to a folder that exists,
 * and Spring Security must allow the path through. Because {@code anyRequest().authenticated()} is
 * the catch-all, {@code /images/**} used to answer 401 - the resource handler was correct, but the
 * filter chain rejected the request before it ever got there.
 *
 * <p>This runs against a real embedded Tomcat ({@code RANDOM_PORT}) rather than MockMvc on purpose.
 * MockMvc builds a {@code MockHttpServletRequest} whose raw request URI still carries the
 * percent-encoding, and Spring Security's {@code StrictHttpFirewall} rejects it with a bodiless 400
 * for a URL like {@code /images/2/2/VF3_Rear%2034_...webp}. Real Tomcat decodes the path first, so
 * the very same URL is served with 200. Asserting through MockMvc would therefore report a
 * phantom failure that no browser ever sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class StaticImageAccessTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private VehicleImageRepository vehicleImageRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Sends the path verbatim (no client-side re-encoding) and returns the status code. */
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

    /**
     * Walks every {@code vehicle_image.image_url} the seeder wrote and asserts the photo actually
     * resolves. This is the check that catches a renamed/moved {@code images/} folder, a pinned id
     * that no longer matches a folder name, or a filename with characters that survive the DB but
     * break the URL (spaces, parentheses, {@code &}, {@code ,}, {@code =}).
     *
     * <p>Collecting failures into a list instead of failing on the first one reports every broken
     * URL in a single run.
     */
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
