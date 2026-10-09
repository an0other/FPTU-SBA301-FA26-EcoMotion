package com.fptufa26.ecomotion.common.storage.implement;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    @TempDir
    Path root;

    @Test
    void storesAndReadsTheStableObjectKey() {
        LocalFileStorageService storage = new LocalFileStorageService(root.toString(), 1024);
        byte[] image = new byte[]{1, 2, 3};

        String key = storage.store("renter", 42L, "cccd-front.jpg", image, "image/jpeg");

        assertThat(key).isEqualTo("renter/42/cccd-front.jpg");
        assertThat(storage.read(key)).isEqualTo(image);
    }

    @Test
    void rejectsPathsOutsideTheObjectKeyLayout() {
        LocalFileStorageService storage = new LocalFileStorageService(root.toString(), 1024);

        assertThatThrownBy(() -> storage.read("../private.jpg"))
                .isInstanceOf(FileStorageException.class);
    }
}
