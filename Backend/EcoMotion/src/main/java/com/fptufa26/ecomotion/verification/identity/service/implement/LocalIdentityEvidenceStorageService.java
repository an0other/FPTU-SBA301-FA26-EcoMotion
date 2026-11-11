package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class LocalIdentityEvidenceStorageService implements IdentityEvidenceStorageService {

    private final Path root;

    public LocalIdentityEvidenceStorageService(
            @Value("${app.identity.evidence-dir:./storage/identity-evidence}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID attemptId, MultipartFile file, int maxBytes) {
        if (file == null || file.isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_EMPTY", "File tải lên rỗng.");
        }
        if (file.getSize() > maxBytes) {
            throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_FILE_TOO_LARGE", "File vượt giới hạn dung lượng.");
        }
        String key = attemptId + "/" + UUID.randomUUID() + ".bin";
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            ensureNoSymlinks(target);
            try (var input = file.getInputStream();
                 var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > maxBytes) {
                        throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_FILE_TOO_LARGE",
                                "File vượt giới hạn dung lượng.");
                    }
                    output.write(buffer, 0, count);
                }
                if (size == 0) {
                    throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_EMPTY", "File tải lên rỗng.");
                }
            }
            return key;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            if (exception instanceof IdentityAiException failure) {
                throw failure;
            }
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "IDENTITY_EVIDENCE_STORAGE_ERROR",
                    "Không thể lưu bằng chứng xác minh.");
        }
    }

    @Override
    public byte[] read(String key, int maxBytes) {
        Path target = resolve(key);
        try {
            ensureNoSymlinks(target);
            long size = Files.size(target);
            if (size < 1 || size > maxBytes) {
                throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "IDENTITY_EVIDENCE_INVALID",
                        "Bằng chứng đã lưu không hợp lệ.");
            }
            try (var input = Files.newInputStream(target)) {
                byte[] content = input.readNBytes(maxBytes + 1);
                if (content.length < 1 || content.length > maxBytes) {
                    throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "IDENTITY_EVIDENCE_INVALID",
                            "Bằng chứng đã lưu không hợp lệ.");
                }
                return content;
            }
        } catch (IOException exception) {
            throw failure(HttpStatus.NOT_FOUND, "IDENTITY_EVIDENCE_NOT_FOUND", "Không tìm thấy bằng chứng.");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        Path target = resolve(key);
        try {
            ensureNoSymlinks(target);
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "IDENTITY_EVIDENCE_STORAGE_ERROR",
                    "Không thể dọn bằng chứng xác minh.");
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}\\.bin")) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_EVIDENCE_INVALID", "Mã bằng chứng không hợp lệ.");
        }
        String[] parts = key.split("/");
        UUID.fromString(parts[0]);
        UUID.fromString(parts[1].substring(0, 36));
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_EVIDENCE_INVALID", "Mã bằng chứng không hợp lệ.");
        }
        return target;
    }

    private void ensureNoSymlinks(Path target) throws IOException {
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
                throw new IOException("Symbolic links are not allowed for identity evidence");
            }
        }
    }

    private IdentityAiException failure(HttpStatus status, String code, String message) {
        return new IdentityAiException(status, code, message);
    }
}
