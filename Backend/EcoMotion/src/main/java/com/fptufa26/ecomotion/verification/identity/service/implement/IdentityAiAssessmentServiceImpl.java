package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiClient;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiUnavailableException;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiAssessmentResponse.DocumentRead;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityVideoResult;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import com.fptufa26.ecomotion.verification.identity.integration.IdentityVerificationClient;
import com.fptufa26.ecomotion.verification.identity.service.IdentityAiAssessmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class IdentityAiAssessmentServiceImpl implements IdentityAiAssessmentService {

    private final UserService users;
    private final DocumentAiClient documents;
    private final IdentityVerificationClient videos;
    private final boolean enabled;
    private final int maxImageBytes;
    private final int maxVideoBytes;
    private final long maxImagePixels;
    private final Semaphore capacity = new Semaphore(1);

    public IdentityAiAssessmentServiceImpl(
            UserService users, DocumentAiClient documents, IdentityVerificationClient videos,
            @Value("${app.identity.ai.enabled:false}") boolean enabled,
            @Value("${app.identity.ai.max-image-bytes:8388608}") int maxImageBytes,
            @Value("${app.identity.ai.max-video-bytes:20971520}") int maxVideoBytes,
            @Value("${app.identity.ai.max-image-pixels:20000000}") long maxImagePixels) {
        this.users = users;
        this.documents = documents;
        this.videos = videos;
        this.enabled = enabled;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
        this.maxImagePixels = maxImagePixels;
        if (maxImageBytes < 1 || maxVideoBytes < 1 || maxImageBytes == Integer.MAX_VALUE
                || maxVideoBytes == Integer.MAX_VALUE || maxImagePixels < 1) {
            throw new IllegalArgumentException("Identity AI upload limits are invalid");
        }
    }

    @Override
    public IdentityAiAssessmentResponse assess(
            Long userId, MultipartFile front, MultipartFile back, MultipartFile video) {
        requireAllowed(userId);
        acquireCapacity();
        try {
            UploadedImage frontImage = readImage(front);
            UploadedImage backImage = readImage(back);
            byte[] videoBytes = readBytes(video, maxVideoBytes);
            DocumentRead document = callDocuments(frontImage, backImage);
            UUID requestId = UUID.randomUUID();
            if (!document.requiredFieldsReadable()) {
                return new IdentityAiAssessmentResponse(requestId, document, null, "RETRY_REQUIRED",
                        List.of("DOCUMENT_RETAKE_REQUIRED"), "NOT_EVALUATED", false, true);
            }
            var result = videos.assess(frontImage.content(), "front." + frontImage.extension(), videoBytes);
            List<String> reasons = result.workflow().reasons() == null
                    ? List.of() : List.copyOf(result.workflow().reasons());
            return new IdentityAiAssessmentResponse(requestId, document, result,
                    result.workflow().status(), reasons, "NOT_EVALUATED", false, true);
        } finally {
            capacity.release();
        }
    }

    @Override
    public DocumentRead readDocuments(Long userId, byte[] front, byte[] back) {
        requireAllowed(userId);
        acquireCapacity();
        try {
            return callDocuments(inspectImage(front), inspectImage(back));
        } finally {
            capacity.release();
        }
    }

    @Override
    public IdentityVideoResult assessVideo(Long userId, byte[] front, byte[] video) {
        requireAllowed(userId);
        acquireCapacity();
        try {
            var reference = inspectImage(front);
            requireLength(video, maxVideoBytes);
            return videos.assess(reference.content(), "front." + reference.extension(), video);
        } finally {
            capacity.release();
        }
    }

    private void requireAllowed(Long userId) {
        var user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Account not found"));
        if ((user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.PENDING)
                || (user.getRole() != Role.RENTER && user.getRole() != Role.PROVIDER)) {
            throw new AccessDeniedException("Account cannot request identity assessment");
        }
        if (!enabled) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_DISABLED",
                    "Tích hợp AI chưa được bật trên backend.");
        }
    }

    private void acquireCapacity() {
        if (!capacity.tryAcquire()) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_BUSY",
                    "Backend đang xử lý một lượt xác minh, vui lòng thử lại.");
        }
    }

    private DocumentRead callDocuments(UploadedImage front, UploadedImage back) {
        try {
            return parseDocument(documents.readCitizenId(front.content(), "front." + front.extension(),
                    back.content(), "back." + back.extension()));
        } catch (DocumentAiUnavailableException exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "DOCUMENT_AI_UNAVAILABLE",
                    "Không thể nhận kết quả đọc CCCD từ DocumentAI.");
        }
    }

    private DocumentRead parseDocument(Map<String, Object> raw) {
        if (raw == null) {
            throw failure(HttpStatus.BAD_GATEWAY, "DOCUMENT_AI_INVALID_RESPONSE",
                    "DocumentAI trả kết quả không hợp lệ.");
        }
        String type = text(raw.get("documentType"));
        String status = text(raw.get("status"));
        if (type == null || status == null) {
            throw failure(HttpStatus.BAD_GATEWAY, "DOCUMENT_AI_INVALID_RESPONSE",
                    "DocumentAI trả kết quả không hợp lệ.");
        }
        Map<?, ?> data = raw.get("data") instanceof Map<?, ?> map ? map : Map.of();
        String number = text(data.get("documentNumber"));
        String name = text(data.get("fullName"));
        String birth = text(data.get("dateOfBirth"));
        boolean readable = "VN_CITIZEN_ID".equals(type)
                && "PROCESSED".equals(text(raw.get("code")))
                && Set.of("READ_SUCCESS", "REVIEW_REQUIRED", "PARTIALLY_READ").contains(status)
                && number != null && number.matches("[0-9]{12}")
                && name != null && validBirthDate(birth);
        return new DocumentRead(type, status, text(raw.get("code")), number, name, birth,
                text(data.get("expiryDate")), text(data.get("sex")), text(data.get("address")), readable,
                com.fptufa26.ecomotion.verification.documentai.DocumentAiEvidence.from(raw));
    }

    private boolean validBirthDate(String value) {
        if (value == null) {
            return false;
        }
        try {
            return !LocalDate.parse(value).isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string.trim() : null;
    }

    private UploadedImage readImage(MultipartFile file) {
        return inspectImage(readBytes(file, maxImageBytes));
    }

    private UploadedImage inspectImage(byte[] content) {
        requireLength(content, maxImageBytes);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IDENTITY_IMAGE_INVALID",
                        "Ảnh CCCD phải là JPEG hoặc PNG hợp lệ.");
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                String extension;
                if ("JPEG".equalsIgnoreCase(format)) {
                    extension = "jpg";
                } else if ("PNG".equalsIgnoreCase(format)) {
                    extension = "png";
                } else {
                    throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IDENTITY_IMAGE_INVALID",
                            "Ảnh CCCD chỉ hỗ trợ JPEG hoặc PNG.");
                }
                if ((long) reader.getWidth(0) * reader.getHeight(0) > maxImagePixels) {
                    throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_IMAGE_TOO_LARGE",
                            "Độ phân giải ảnh CCCD vượt giới hạn cho phép.");
                }
                if (reader.read(0) == null) {
                    throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_IMAGE_INVALID", "Không thể đọc ảnh CCCD.");
                }
                return new UploadedImage(content, extension);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_IMAGE_INVALID", "Không thể đọc ảnh CCCD.");
        }
    }

    private byte[] readBytes(MultipartFile file, int limit) {
        if (file == null || file.isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_EMPTY", "Cần tải lên đầy đủ ảnh và video.");
        }
        if (file.getSize() > limit) {
            throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_FILE_TOO_LARGE",
                    "File vượt giới hạn dung lượng cho phép.");
        }
        try (var input = file.getInputStream()) {
            byte[] content = input.readNBytes(limit + 1);
            if (content.length > limit) {
                throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_FILE_TOO_LARGE",
                        "File vượt giới hạn dung lượng cho phép.");
            }
            if (content.length == 0) {
                throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_EMPTY", "File tải lên rỗng.");
            }
            return content;
        } catch (IOException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_UNREADABLE", "Không thể đọc file tải lên.");
        }
    }

    private void requireLength(byte[] content, int limit) {
        if (content == null || content.length == 0) {
            throw failure(HttpStatus.BAD_REQUEST, "IDENTITY_FILE_EMPTY", "File tải lên rỗng.");
        }
        if (content.length > limit) {
            throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "IDENTITY_FILE_TOO_LARGE",
                    "File vượt giới hạn dung lượng cho phép.");
        }
    }

    private IdentityAiException failure(HttpStatus status, String code, String message) {
        return new IdentityAiException(status, code, message);
    }

    private record UploadedImage(byte[] content, String extension) {
    }
}
