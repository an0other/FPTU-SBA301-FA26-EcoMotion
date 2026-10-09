package com.fptufa26.ecomotion.common.storage.implement;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.common.storage.StorageObjectPath;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path root;
    private final long maxBytes;

    public LocalFileStorageService(
            @Value("${app.upload.dir}") String rootDir,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
    public String store(String entity, Long userId, String filename, byte[] content, String contentType) {
        String key = StorageObjectPath.forUpload(entity, userId, filename, content, contentType, maxBytes);
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new FileStorageException("Invalid file name");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new FileStorageException("Could not write the uploaded file to disk", ex);
        }
        return key;
    }

    @Override
    public byte[] read(String storedPath) {
        Path target = root.resolve(StorageObjectPath.validateStoredPath(storedPath)).normalize();
        if (!target.startsWith(root)) {
            throw new FileStorageException("Invalid stored path");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the stored file", ex);
        }
    }
}
