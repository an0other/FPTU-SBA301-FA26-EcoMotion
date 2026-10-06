package com.fptufa26.ecomotion.auth.storage;

import com.fptufa26.ecomotion.auth.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_ENTITIES = Set.of("renter", "provider", "driver", "vehicle", "booking");

    private final Path root;
    private final long maxBytes;

    public FileStorageService(
            @Value("${app.upload.dir}") String rootDir,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxBytes) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public String store(String entity, Long userId, String filename, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new FileStorageException("Uploaded file is empty");
        }
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new FileStorageException("Only image uploads are accepted");
        }
        if (content.length > maxBytes) {
            throw new FileStorageException("Uploaded file exceeds the maximum allowed size");
        }
        if (userId == null) {
            throw new FileStorageException("A user id is required to store a file");
        }

        Path entityDir = resolveEntityDir(entity);
        String safeName = sanitize(filename);
        Path userDir = entityDir.resolve(String.valueOf(userId)).normalize();
        Path target = userDir.resolve(safeName).normalize();
        if (!target.startsWith(userDir)) {
            throw new FileStorageException("Invalid file name");
        }

        try {
            Files.createDirectories(userDir);
            Files.write(target, content);
        } catch (IOException ex) {
            throw new FileStorageException("Could not write the uploaded file to disk", ex);
        }

        return entity.trim().toLowerCase() + "/" + userId + "/" + safeName;
    }

    public byte[] read(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new FileStorageException("A stored path is required");
        }
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            throw new FileStorageException("Invalid stored path");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read the stored file", ex);
        }
    }

    private Path resolveEntityDir(String entity) {
        if (entity == null || entity.isBlank()) {
            throw new FileStorageException("An entity name is required");
        }
        String normalized = entity.trim().toLowerCase();
        if (!ALLOWED_ENTITIES.contains(normalized)) {
            throw new FileStorageException("Unsupported upload entity: " + entity);
        }
        return root.resolve(normalized).normalize();
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        String cleaned = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "file" : cleaned;
    }
}
