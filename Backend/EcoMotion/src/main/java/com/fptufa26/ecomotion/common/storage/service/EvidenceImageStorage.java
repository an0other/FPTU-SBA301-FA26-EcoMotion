package com.fptufa26.ecomotion.common.storage.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvidenceImageStorage {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final String URL_PREFIX = "/api/files/";

    private final FileStorageService fileStorageService;

    public StoredEvidence store(MultipartFile file, Long uploaderUserId, String purpose) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn hình ảnh để tải lên");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Ảnh vượt quá 5 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được ảnh tải lên");
        }
        String ext = detectExtension(content);
        if (ext == null) {
            throw new BadRequestException("Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        }
        String contentType = switch (ext) {
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
        String safePurpose = purpose == null ? "evidence" : purpose.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
        String key = fileStorageService.store("booking", uploaderUserId,
                safePurpose + "-" + UUID.randomUUID() + ext, content, contentType);
        return new StoredEvidence(URL_PREFIX + key, key);
    }

    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) return;
        try {
            fileStorageService.delete(key);
        } catch (RuntimeException ignored) {
        }
    }

    private static String detectExtension(byte[] c) {
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) return ".jpg";
        if (c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G') return ".png";
        if (c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P') return ".webp";
        return null;
    }

    public record StoredEvidence(String url, String key) {}
}
