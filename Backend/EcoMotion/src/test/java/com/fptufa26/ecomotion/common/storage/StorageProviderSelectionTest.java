package com.fptufa26.ecomotion.common.storage;

import com.fptufa26.ecomotion.common.storage.implement.SupabaseFileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "app.upload.provider=supabase",
        "app.upload.supabase.url=http://127.0.0.1:1",
        "app.upload.supabase.bucket=identity",
        "app.upload.supabase.service-key=test-secret"
})
class StorageProviderSelectionTest {

    @Autowired
    private FileStorageService storage;

    @Test
    void selectsSupabaseWithoutChangingTheConsumer() {
        assertThat(storage).isInstanceOf(SupabaseFileStorageService.class);
    }
}
