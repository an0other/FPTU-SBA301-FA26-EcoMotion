package com.fptufa26.ecomotion.common.storage;

import java.util.Locale;
import java.util.Set;

public final class StorageObjectPath {

    private static final Set<String> ALLOWED_ENTITIES = Set.of(
            "renter", "provider", "driver", "vehicle", "booking", "payment-evidence");

    private StorageObjectPath() {
    }

    public static String forUpload(String entity, Long userId, String filename, byte[] content,
                                   String contentType, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new FileStorageException("Uploaded file is empty");
        }
        boolean isImage = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        boolean isPdf = contentType != null && contentType.equalsIgnoreCase("application/pdf");
        if (!isImage && !isPdf) {
            throw new FileStorageException("Only image (JPG, PNG) or PDF document uploads are accepted");
        }
        if (content.length > maxBytes) {
            throw new FileStorageException("Uploaded file exceeds the maximum allowed size");
        }
        if (userId == null) {
            throw new FileStorageException("A user id is required to store a file");
        }
        if (entity == null || !ALLOWED_ENTITIES.contains(entity.trim().toLowerCase(Locale.ROOT))) {
            throw new FileStorageException("Unsupported upload entity: " + entity);
        }
        String safeName = filename == null || filename.isBlank()
                ? "file" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.isBlank()) {
            safeName = "file";
        }
        if (safeName.equals(".") || safeName.equals("..")) {
            throw new FileStorageException("Invalid file name");
        }
        return entity.trim().toLowerCase(Locale.ROOT) + "/" + userId + "/" + safeName;
    }

    public static String validateStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new FileStorageException("A stored path is required");
        }
        String[] parts = storedPath.split("/", -1);
        if (parts.length != 3 || !ALLOWED_ENTITIES.contains(parts[0])
                || !parts[1].matches("[0-9]+")
                || !parts[2].matches("[a-zA-Z0-9._-]+")
                || parts[2].equals(".") || parts[2].equals("..")) {
            throw new FileStorageException("Invalid stored path");
        }
        return storedPath;
    }
}
