package com.fptufa26.ecomotion.user.service.implement;


import com.fptufa26.ecomotion.common.storage.service.FileStorageService;
import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseClassOptionResponse;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseImage;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.exception.RenterProfileNotFoundException;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.service.RenterDrivingLicenseService;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RenterDrivingLicenseServiceImpl implements RenterDrivingLicenseService {

    private final RenterRepository renterRepository;
    private final FileStorageService fileStorageService;

    public RenterDrivingLicenseServiceImpl(RenterRepository renterRepository, FileStorageService fileStorageService) {
        this.renterRepository = renterRepository;
        this.fileStorageService = fileStorageService;
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
        throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                org.springframework.http.HttpStatus.GONE, "LICENSE_REVIEW_REQUIRED", "Hãy dùng luồng nộp GPLX để Admin xét duyệt.");
    }

    @Override
    public DrivingLicenseResponse uploadDrivingLicense(Long userId, MultipartFile front, MultipartFile back) {
        throw new com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException(
                org.springframework.http.HttpStatus.GONE, "LICENSE_REVIEW_REQUIRED", "Hãy cập nhật giao diện và nộp GPLX để Admin xét duyệt.");
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

}
