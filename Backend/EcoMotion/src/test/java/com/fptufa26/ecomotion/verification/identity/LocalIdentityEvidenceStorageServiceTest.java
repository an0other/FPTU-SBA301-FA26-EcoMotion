package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.service.implement.LocalIdentityEvidenceStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalIdentityEvidenceStorageServiceTest {
    @TempDir
    Path directory;

    @Test
    void storesOpaqueNameAndReadsAndDeletesEvidence() {
        var storage = new LocalIdentityEvidenceStorageService(directory.toString());
        byte[] content = {1, 2, 3};
        String key = storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", "citizen-number.jpg", "image/jpeg", content), 8);
        assertFalse(key.contains("citizen"));
        assertArrayEquals(content, storage.read(key, 8));
        storage.delete(key);
        assertFalse(Files.exists(directory.resolve(key)));
    }

    @Test
    void rejectsOversizedAndEmptyUploads() {
        var storage = new LocalIdentityEvidenceStorageService(directory.toString());
        assertThrows(IdentityAiException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", new byte[9]), 8));
        assertThrows(IdentityAiException.class, () -> storage.store(UUID.randomUUID(),
                new MockMultipartFile("file", new byte[0]), 8));
    }

    @Test
    void rejectsTraversalAndBoundedRead() {
        var storage = new LocalIdentityEvidenceStorageService(directory.toString());
        assertThrows(IdentityAiException.class, () -> storage.read("../outside", 8));
        assertThrows(IdentityAiException.class, () -> storage.delete("../outside"));
        String key = storage.store(UUID.randomUUID(), new MockMultipartFile("file", new byte[8]), 8);
        assertThrows(IdentityAiException.class, () -> storage.read(key, 7));
    }
}
