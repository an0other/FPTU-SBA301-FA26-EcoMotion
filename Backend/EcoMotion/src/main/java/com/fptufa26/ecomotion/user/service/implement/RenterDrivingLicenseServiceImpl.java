package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.common.storage.FileStorageException;
import com.fptufa26.ecomotion.common.storage.FileStorageService;
import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseClassOptionResponse;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseImage;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.exception.DrivingLicenseAlreadyExistsException;
import com.fptufa26.ecomotion.user.exception.RenterProfileNotFoundException;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.service.RenterDrivingLicenseService;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiClient;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiReadRejectedException;
import com.fptufa26.ecomotion.verification.documentai.DocumentAiReadResult;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RenterDrivingLicenseServiceImpl implements RenterDrivingLicenseService {

    private static final String STORAGE_ENTITY = "renter";
    private static final String UNLIMITED_EXPIRY = "UNLIMITED";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RenterRepository renterRepository;
    private final DocumentAiClient documentAiClient;
    private final FileStorageService fileStorageService;
    private final long maxImageBytes;

    public RenterDrivingLicenseServiceImpl(
            RenterRepository renterRepository,
            DocumentAiClient documentAiClient,
            FileStorageService fileStorageService,
            @Value("${app.upload.max-file-size-bytes:8388608}") long maxImageBytes) {
        this.renterRepository = renterRepository;
        this.documentAiClient = documentAiClient;
        this.fileStorageService = fileStorageService;
        this.maxImageBytes = maxImageBytes;
    }

    @Override
    public DrivingLicenseResponse getDrivingLicense(Long userId) {
        Renter renter = renterRepository.findByUser_Id(userId)
                .orElseThrow(RenterProfileNotFoundException::new);
        return toResponse(renter);
    }

    @Override
    @Transactional
    public DrivingLicenseResponse updateDrivingLicense(
            Long userId,
            UpdateDrivingLicenseRequest request) {
        validateRequest(request);
        String licenseNo = request.licenseNo().trim().toUpperCase(Locale.ROOT);
        String licenseClass = request.licenseClass().trim().toUpperCase(Locale.ROOT);
        Renter renter = renterRepository.findRenterByUser_Id(userId)
                .orElseThrow(RenterProfileNotFoundException::new);

        if (renterRepository.existsByLicenseNoAndIdNot(licenseNo, renter.getId())) {
            throw new DrivingLicenseAlreadyExistsException();
        }

        boolean changed = !Objects.equals(renter.getLicenseNo(), licenseNo)
                || !Objects.equals(renter.getLicenseClass(), licenseClass)
                || !Objects.equals(renter.getLicenseExpiry(), request.licenseExpiry());
        if (changed) {
            renter.setLicenseNo(licenseNo);
            renter.setLicenseClass(licenseClass);
            renter.setLicenseExpiry(request.licenseExpiry());
            renter.setLicenseExpiryUnlimited(false);
            renter.setLicenseVerified(false);
            List<String> oldImages = clearAiRead(renter);
            renterRepository.flush();
            registerAfterCommitCleanup(oldImages);
        }
        return toResponse(renter);
    }

    @Override
    @Transactional
    public DrivingLicenseResponse uploadDrivingLicense(
            Long userId,
            MultipartFile front,
            MultipartFile back) {
        UploadedImage frontImage = readImage(front, "trước");
        UploadedImage backImage = readImage(back, "sau");
        Map<String, Object> response = documentAiClient.readDrivingLicense(
                frontImage.content(), frontImage.filename(), backImage.content(), backImage.filename());
        DocumentAiReadResult result = parseSuccessfulRead(response);
        String normalizedNumber = result.documentNumber().trim().toUpperCase(Locale.ROOT);
        if (normalizedNumber.length() > 20) {
            throw new DocumentAiReadRejectedException("Số GPLX đọc được không hợp lệ. Vui lòng kiểm tra ảnh.");
        }

        Renter renter = renterRepository.findRenterByUser_Id(userId)
                .orElseThrow(RenterProfileNotFoundException::new);
        if (renterRepository.existsByLicenseNoAndIdNot(normalizedNumber, renter.getId())) {
            throw new DrivingLicenseAlreadyExistsException();
        }

        String uploadId = UUID.randomUUID().toString();
        String frontPath = fileStorageService.store(STORAGE_ENTITY, userId,
                "driving-license-front-" + uploadId + frontImage.extension(),
                frontImage.content(), frontImage.mediaType().toString());
        String backPath;
        try {
            backPath = fileStorageService.store(STORAGE_ENTITY, userId,
                    "driving-license-back-" + uploadId + backImage.extension(),
                    backImage.content(), backImage.mediaType().toString());
        } catch (RuntimeException ex) {
            deleteAfterFailure(frontPath, ex);
            throw ex;
        }

        List<String> oldImages = clearAiRead(renter);
        renter.setLicenseNo(normalizedNumber);
        renter.setLicenseFullName(result.fullName().trim());
        renter.setLicenseDateOfBirth(result.dateOfBirth());
        renter.setLicenseClass(result.licenseClass().trim().toUpperCase(Locale.ROOT));
        renter.setLicenseIssueDate(result.issueDate());
        renter.setLicenseExpiry(result.expiryDate());
        renter.setLicenseExpiryUnlimited(result.expiryUnlimited());
        renter.setLicenseVerified(true);
        renter.setLicenseFrontImagePath(frontPath);
        renter.setLicenseBackImagePath(backPath);

        registerRollbackCleanup(frontPath);
        registerRollbackCleanup(backPath);
        renterRepository.flush();
        registerAfterCommitCleanup(oldImages);
        return toResponse(renter);
    }

    @Override
    public List<DrivingLicenseClassOptionResponse> getSupportedLicenseClasses() {
        return DrivingLicenseEligibilityPolicy.supportedClasses();
    }

    @Override
    public Optional<DrivingLicenseImage> getDrivingLicenseImage(Long userId, String side) {
        Renter renter = renterRepository.findByUser_Id(userId)
                .orElseThrow(RenterProfileNotFoundException::new);
        String path = switch (side == null ? "" : side.toLowerCase(Locale.ROOT)) {
            case "front" -> renter.getLicenseFrontImagePath();
            case "back" -> renter.getLicenseBackImagePath();
            default -> throw new IllegalArgumentException("GPLX side phải là 'front' hoặc 'back'.");
        };
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        MediaType mediaType = path.toLowerCase(Locale.ROOT).endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return Optional.of(new DrivingLicenseImage(fileStorageService.read(path), mediaType));
    }

    private void validateRequest(UpdateDrivingLicenseRequest request) {
        if (request == null
                || request.licenseNo() == null
                || request.licenseNo().isBlank()
                || request.licenseNo().length() > 20
                || request.licenseClass() == null
                || request.licenseClass().isBlank()
                || request.licenseClass().length() > 10
                || request.licenseExpiry() == null
                || request.licenseExpiry().isBefore(LocalDate.now(VIETNAM_ZONE))) {
            throw new IllegalArgumentException("Thông tin GPLX không hợp lệ.");
        }
    }

    private DrivingLicenseResponse toResponse(Renter renter) {
        boolean eligible = DrivingLicenseEligibilityPolicy.isEligibleForYearlyRental(
                renter.getLicenseClass(),
                renter.getLicenseIssueDate(),
                renter.getLicenseExpiry(),
                renter.isLicenseExpiryUnlimited());
        return new DrivingLicenseResponse(
                renter.getLicenseNo(),
                renter.getLicenseFullName(),
                renter.getLicenseDateOfBirth(),
                renter.getLicenseClass(),
                renter.getLicenseIssueDate(),
                renter.getLicenseExpiry(),
                renter.isLicenseExpiryUnlimited(),
                renter.isLicenseVerified(),
                renter.isLicenseVerified() && eligible,
                DrivingLicenseEligibilityPolicy.ineligibleReason(
                        renter.getLicenseClass(),
                        renter.getLicenseIssueDate(),
                        renter.getLicenseExpiry(),
                        renter.isLicenseExpiryUnlimited()),
                renter.getLicenseFrontImagePath() != null,
                renter.getLicenseBackImagePath() != null);
    }

    private UploadedImage readImage(MultipartFile file, String side) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Cần tải lên ảnh mặt " + side + " của GPLX.");
        }
        if (file.getSize() > maxImageBytes) {
            throw new FileStorageException("Ảnh GPLX vượt quá dung lượng tối đa cho phép.");
        }
        String contentType = file.getContentType();
        if (!MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)
                && !MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            throw new FileStorageException("Ảnh GPLX chỉ hỗ trợ định dạng JPG hoặc PNG.");
        }
        try {
            byte[] content = file.getBytes();
            if (!hasValidImageSignature(content, contentType)) {
                throw new FileStorageException("Nội dung ảnh GPLX không khớp định dạng JPG hoặc PNG.");
            }
            String extension = MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType) ? ".png" : ".jpg";
            return new UploadedImage(content, contentType, extension);
        } catch (IOException ex) {
            throw new FileStorageException("Không thể đọc ảnh GPLX đã tải lên.", ex);
        }
    }

    private DocumentAiReadResult parseSuccessfulRead(Map<String, Object> response) {
        if (!"READ_SUCCESS".equals(value(response.get("status")))
                || !"VN_DRIVING_LICENSE".equals(value(response.get("documentType")))) {
            throw new DocumentAiReadRejectedException(
                    "AI chưa đọc được GPLX đầy đủ. Vui lòng tải ảnh rõ cả hai mặt và thử lại.");
        }
        Map<String, Object> assessment = object(response.get("assessment"));
        Map<String, Object> reading = object(assessment.get("readingAssessment"));
        Map<String, Object> comparison = object(response.get("coreComparison"));
        if (!"ALL_MAIN_FIELDS_READ".equals(value(reading.get("status")))
                || !"MATCH".equals(value(comparison.get("status")))) {
            throw new DocumentAiReadRejectedException(
                    "Thông tin OCR và mã QR chưa khớp đầy đủ. Vui lòng kiểm tra lại ảnh GPLX.");
        }

        Map<String, Object> data = object(response.get("data"));
        String documentNumber = required(data, "documentNumber");
        String fullName = required(data, "fullName");
        String licenseClass = required(data, "licenseClass");
        LocalDate dateOfBirth = parseDate(required(data, "dateOfBirth"), "ngày sinh");
        LocalDate issueDate = parseDate(required(data, "issueDate"), "ngày cấp");
        String expiryText = required(data, "expiryDate");
        boolean unlimited = UNLIMITED_EXPIRY.equalsIgnoreCase(expiryText);
        LocalDate expiryDate = unlimited ? null : parseDate(expiryText, "ngày hết hạn");
        LocalDate today = LocalDate.now(VIETNAM_ZONE);
        if (dateOfBirth.isAfter(today) || issueDate.isAfter(today)
                || (!unlimited && (expiryDate.isBefore(issueDate) || expiryDate.isBefore(dateOfBirth)))) {
            throw new DocumentAiReadRejectedException(
                    "Ngày tháng đọc được trên GPLX không hợp lệ. Vui lòng kiểm tra ảnh và thử lại.");
        }
        return new DocumentAiReadResult(
                documentNumber,
                fullName,
                dateOfBirth,
                licenseClass,
                issueDate,
                expiryDate,
                unlimited,
                value(response.get("requestId")));
    }

    private LocalDate parseDate(String value, String label) {
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                DateTimeFormatter.ofPattern("dd-MM-uuuu"));
        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try the next supported display format.
            }
        }
        throw new DocumentAiReadRejectedException("AI chưa đọc được " + label + " trên GPLX.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String required(Map<String, Object> data, String key) {
        String value = value(data.get(key));
        if (value == null || value.isBlank()) {
            throw new DocumentAiReadRejectedException("AI chưa đọc được đầy đủ thông tin GPLX.");
        }
        return value.trim();
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasValidImageSignature(byte[] bytes, String contentType) {
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        }
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType) || bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private List<String> clearAiRead(Renter renter) {
        List<String> oldImages = new ArrayList<>();
        addIfPresent(oldImages, renter.getLicenseFrontImagePath());
        addIfPresent(oldImages, renter.getLicenseBackImagePath());
        renter.setLicenseFullName(null);
        renter.setLicenseDateOfBirth(null);
        renter.setLicenseIssueDate(null);
        renter.setLicenseExpiryUnlimited(false);
        renter.setLicenseFrontImagePath(null);
        renter.setLicenseBackImagePath(null);
        return oldImages;
    }

    private void registerRollbackCleanup(String storedPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    try {
                        fileStorageService.delete(storedPath);
                    } catch (RuntimeException ex) {
                        log.error("Could not clean up a GPLX image after transaction rollback", ex);
                    }
                }
            }
        });
    }

    private void registerAfterCommitCleanup(List<String> storedPaths) {
        if (storedPaths.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String storedPath : storedPaths) {
                    try {
                        fileStorageService.delete(storedPath);
                    } catch (RuntimeException ex) {
                        log.error("Could not remove a replaced GPLX image from private storage", ex);
                    }
                }
            }
        });
    }

    private void deleteAfterFailure(String storedPath, RuntimeException original) {
        try {
            fileStorageService.delete(storedPath);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private void addIfPresent(List<String> paths, String path) {
        if (path != null && !path.isBlank()) {
            paths.add(path);
        }
    }

    private record UploadedImage(byte[] content, String contentType, String extension) {
        MediaType mediaType() {
            return MediaType.parseMediaType(contentType);
        }

        String filename() {
            return "driving-license" + extension;
        }
    }
}
