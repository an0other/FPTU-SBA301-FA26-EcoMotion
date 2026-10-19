package com.fptufa26.ecomotion.common.storage.implement;

import com.fptufa26.ecomotion.common.storage.DocumentStorageObjectPath;
import com.fptufa26.ecomotion.common.storage.DocumentStorageService;
import com.fptufa26.ecomotion.common.storage.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
@ConditionalOnProperty(
        name = "app.document-storage.provider",
        havingValue = "local",
        matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private final Path root;
    private final long maxBytes;

    public LocalDocumentStorageService(
            @Value("${app.document-storage.dir:${app.upload.dir:./uploads}}") String rootDir,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @Override
    public String store(
            String entity,
            Long userId,
            String filename,
            byte[] content,
            String contentType) {
        String key = DocumentStorageObjectPath.forUpload(
                entity,
                userId,
                filename,
                content,
                contentType,
                maxBytes);

        Path target = resolvePath(key);

        try {
            Files.createDirectories(target.getParent());
            Files.write(
                    target,
                    content,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not store the document",
                    ex);
        }

        return key;
    }

    @Override
    public byte[] read(String storedPath) {
        Path target = resolvePath(
                DocumentStorageObjectPath.validateStoredPath(storedPath));

        try {
            if (Files.size(target) > maxBytes) {
                throw new FileStorageException(
                        "Stored document exceeds the maximum allowed size");
            }

            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not read the document",
                    ex);
        }
    }

    private Path resolvePath(String key) {
        Path target = root.resolve(key).normalize();

        if (!target.startsWith(root)) {
            throw new FileStorageException(
                    "Invalid document path");
        }

        return target;
    }
}