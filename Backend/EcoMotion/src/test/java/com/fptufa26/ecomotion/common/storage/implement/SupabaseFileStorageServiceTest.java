package com.fptufa26.ecomotion.common.storage.implement;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseFileStorageServiceTest {

    private HttpServer server;
    private SupabaseFileStorageService storage;
    private final AtomicReference<String> uploadPath = new AtomicReference<>();
    private final AtomicReference<String> downloadPath = new AtomicReference<>();
    private final AtomicReference<byte[]> uploadedBytes = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> upsert = new AtomicReference<>();
    private final AtomicBoolean rejectUpload = new AtomicBoolean();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            if (exchange.getRequestMethod().equals("POST")) {
                uploadPath.set(exchange.getRequestURI().getPath());
                upsert.set(exchange.getRequestHeaders().getFirst("x-upsert"));
                uploadedBytes.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(rejectUpload.get() ? 403 : 200, -1);
            } else {
                downloadPath.set(exchange.getRequestURI().getPath());
                byte[] image = uploadedBytes.get();
                exchange.sendResponseHeaders(200, image.length);
                exchange.getResponseBody().write(image);
            }
            exchange.close();
        });
        server.start();
        storage = new SupabaseFileStorageService(
                "http://127.0.0.1:" + server.getAddress().getPort(), "identity", "test-secret",
                1024, Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void uploadsAndDownloadsFromThePrivateBucketUsingTheSameKey() {
        byte[] image = new byte[]{1, 2, 3};

        String key = storage.store("renter", 42L, "cccd-front.jpg", image, "image/jpeg");

        assertThat(key).isEqualTo("renter/42/cccd-front.jpg");
        assertThat(uploadPath.get()).isEqualTo("/storage/v1/object/identity/" + key);
        assertThat(upsert.get()).isEqualTo("true");
        assertThat(uploadedBytes.get()).isEqualTo(image);
        assertThat(storage.read(key)).isEqualTo(image);
        assertThat(downloadPath.get()).isEqualTo("/storage/v1/object/authenticated/identity/" + key);
        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        assertThat(apiKey.get()).isEqualTo("test-secret");
    }

    @Test
    void failsWhenSupabaseRejectsTheUpload() {
        rejectUpload.set(true);

        assertThatThrownBy(() -> storage.store("renter", 42L, "cccd-front.jpg", new byte[]{1}, "image/jpeg"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void rejectsAnInvalidObjectKeyBeforeMakingARequest() {
        assertThatThrownBy(() -> storage.read("../private.jpg"))
                .isInstanceOf(FileStorageException.class);
        assertThat(downloadPath.get()).isNull();
    }

    @Test
    void rejectsAnInsecureRemoteOriginBeforeUsingTheSecretKey() {
        assertThatThrownBy(() -> new SupabaseFileStorageService(
                "http://storage.example.com", "identity", "test-secret", 1024,
                Duration.ofSeconds(2), Duration.ofSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
