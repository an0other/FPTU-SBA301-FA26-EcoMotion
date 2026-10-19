package com.fptufa26.ecomotion.common.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class DocumentStorageObjectPath {

    private static final byte[] PDF_SIGNATURE =
            "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String BOOKING_ENTITY = "booking";

    private DocumentStorageObjectPath() {
    }

    public static String forUpload(
            String entity,
            Long userId,
            String filename,
            byte[] content,
            String contentType,
            long maxBytes) {
        if (!BOOKING_ENTITY.equals(entity)) {
            throw new FileStorageException(
                    "Unsupported document entity");
        }

        if (userId == null || userId <= 0) {
            throw new FileStorageException(
                    "A valid user id is required");
        }

        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)) {
            throw new FileStorageException(
                    "Only PDF documents are accepted");
        }

        if (content == null || content.length == 0) {
            throw new FileStorageException(
                    "Uploaded document is empty");
        }

        if (content.length > maxBytes) {
            throw new FileStorageException(
                    "Document exceeds the maximum allowed size");
        }

        if (content.length < PDF_SIGNATURE.length
                || !Arrays.equals(
                PDF_SIGNATURE,
                Arrays.copyOf(content, PDF_SIGNATURE.length))) {
            throw new FileStorageException(
                    "Document does not have a valid PDF header");
        }

        if (filename == null
                || !filename.matches("[a-zA-Z0-9_-]+\\.pdf")) {
            throw new FileStorageException(
                    "Invalid document filename");
        }

        return BOOKING_ENTITY + "/" + userId + "/" + filename;
    }

    public static String validateStoredPath(String storedPath) {
        if (storedPath == null
                || !storedPath.matches(
                "booking/[1-9][0-9]*/[a-zA-Z0-9_-]+\\.pdf")) {
            throw new FileStorageException(
                    "Invalid document path");
        }

        return storedPath;
    }
}