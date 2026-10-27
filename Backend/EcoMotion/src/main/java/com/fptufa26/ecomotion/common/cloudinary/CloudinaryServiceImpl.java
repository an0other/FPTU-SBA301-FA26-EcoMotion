package com.fptufa26.ecomotion.common.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;
    private final CloudinaryConfig cloudinaryConfig;
    private final String folderPrefix;

    public CloudinaryServiceImpl(
            Cloudinary cloudinary,
            CloudinaryConfig cloudinaryConfig,
            @Value("${app.cloudinary.folder-prefix:ecomotion}") String folderPrefix) {
        this.cloudinary = cloudinary;
        this.cloudinaryConfig = cloudinaryConfig;
        this.folderPrefix = (folderPrefix != null && !folderPrefix.isBlank()) ? folderPrefix.trim() : "ecomotion";
    }

    @Override
    public CloudinaryUploadResult uploadTripEvidence(MultipartFile file, Long bookingId) {
        validateFile(file);

        if (bookingId == null) {
            throw new IllegalArgumentException("Mã đơn đặt xe (bookingId) không được để trống");
        }

        if (cloudinaryConfig == null || cloudinaryConfig.isMockOrDemo()) {
            throw new CloudinaryInfrastructureException(
                    "Dịch vụ lưu trữ hình ảnh chưa được cấu hình tài khoản hoặc đang dùng thông tin demo/mock. Vui lòng cấu hình CLOUDINARY_URL hoặc api-key/api-secret hợp lệ."
            );
        }

        String ext = resolveExtension(file);
        String folder = String.format("%s/trip-evidence/%d", folderPrefix, bookingId);

        Map<?, ?> params = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                "overwrite", false
        );

        int maxRetries = 2;
        int attempts = 0;
        while (attempts <= maxRetries) {
            attempts++;
            try {
                log.info("Uploading trip evidence to Cloudinary folder {} for booking {}", folder, bookingId);
                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), params);

                String secureUrl = (String) uploadResult.get("secure_url");
                String publicId = (String) uploadResult.get("public_id");
                String format = (String) uploadResult.get("format");
                Long bytes = uploadResult.get("bytes") instanceof Number n ? n.longValue() : file.getSize();
                Integer width = uploadResult.get("width") instanceof Number n ? n.intValue() : null;
                Integer height = uploadResult.get("height") instanceof Number n ? n.intValue() : null;

                log.info("Successfully uploaded image to Cloudinary: publicId={}, secureUrl={}", publicId, secureUrl);
                return new CloudinaryUploadResult(secureUrl, publicId, format != null ? format : ext, bytes, width, height);
            } catch (IOException ex) {
                log.warn("Cloudinary network/IO failure (attempt {}/{}): {}", attempts, maxRetries + 1, ex.getMessage());
                if (attempts > maxRetries) {
                    throw new CloudinaryInfrastructureException("Lỗi kết nối mạng khi tải ảnh lên Cloudinary: " + ex.getMessage(), ex);
                }
                try {
                    Thread.sleep(300L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CloudinaryInfrastructureException("Tiến trình tải ảnh bị gián đoạn", ie);
                }
            } catch (Exception ex) {
                log.error("Failed to upload trip evidence to Cloudinary for booking {}: {}", bookingId, ex.getMessage());
                throw new CloudinaryInfrastructureException("Không thể tải ảnh lên dịch vụ lưu trữ đám mây Cloudinary: " + ex.getMessage(), ex);
            }
        }
        throw new CloudinaryInfrastructureException("Tải ảnh lên Cloudinary thất bại sau các lần thử lại");
    }

    @Override
    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        if (cloudinaryConfig == null || cloudinaryConfig.isMockOrDemo()) {
            log.warn("Cannot delete image '{}' because Cloudinary credentials are not configured", publicId);
            return;
        }
        try {
            log.info("Deleting image from Cloudinary: publicId={}", publicId);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary destroy result for publicId={}: {}", publicId, result);
        } catch (Exception ex) {
            log.warn("Failed to delete image from Cloudinary (publicId={}): {}", publicId, ex.getMessage());
        }
    }

    private String resolveExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.contains("/")) {
            return contentType.substring(contentType.lastIndexOf('/') + 1).toLowerCase();
        }
        return "jpg";
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File hình ảnh không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dung lượng ảnh vượt quá giới hạn cho phép (tối đa 10 MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Định dạng file không hợp lệ. Chỉ chấp nhận JPG, JPEG, PNG hoặc WEBP");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".exe") || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".cmd")) {
                throw new IllegalArgumentException("Không cho phép tải lên file thực thi");
            }
        }
    }
}
