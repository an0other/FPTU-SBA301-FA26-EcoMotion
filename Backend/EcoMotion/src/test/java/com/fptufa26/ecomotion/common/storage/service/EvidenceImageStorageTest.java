package com.fptufa26.ecomotion.common.storage.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceImageStorageTest {

    private final AtomicReference<String> storedName = new AtomicReference<>();
    private final FileStorageService fake = new FileStorageService() {
        @Override public String store(String entity, Long userId, String filename, byte[] content, String contentType) {
            storedName.set(filename);
            return entity + "/" + userId + "/" + filename;
        }
        @Override public byte[] read(String storedPath) { return new byte[0]; }
        @Override public void delete(String storedPath) { }
    };
    private final EvidenceImageStorage storage = new EvidenceImageStorage(fake);

    @Test
    void storesPngLocallyAndReturnsServableUrl() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};
        var result = storage.store(new MockMultipartFile("file", "a.png", "image/png", png), 5L, "dispute-43");
        assertThat(result.url()).startsWith("/api/files/booking/5/dispute-43-").endsWith(".png");
        assertThat(result.key()).isEqualTo("booking/5/" + storedName.get());
    }

    @Test
    void rejectsNonImageContentEvenWithImageExtension() {
        var fakeJpg = new MockMultipartFile("file", "x.jpg", "image/jpeg", "<script>".getBytes());
        assertThatThrownBy(() -> storage.store(fakeJpg, 5L, "dispute-43"))
                .isInstanceOf(BadRequestException.class);
    }
}
