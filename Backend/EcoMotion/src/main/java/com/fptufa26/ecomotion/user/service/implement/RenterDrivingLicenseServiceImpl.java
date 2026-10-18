package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.user.dto.request.UpdateDrivingLicenseRequest;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseResponse;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.exception.DrivingLicenseAlreadyExistsException;
import com.fptufa26.ecomotion.user.exception.RenterProfileNotFoundException;
import com.fptufa26.ecomotion.user.repository.RenterRepository;
import com.fptufa26.ecomotion.user.service.RenterDrivingLicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RenterDrivingLicenseServiceImpl
        implements RenterDrivingLicenseService {

    private final RenterRepository renterRepository;

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

        String licenseNo = request.licenseNo()
                .trim()
                .toUpperCase(Locale.ROOT);
        String licenseClass = request.licenseClass()
                .trim()
                .toUpperCase(Locale.ROOT);

        Renter renter = renterRepository.findRenterByUser_Id(userId)
                .orElseThrow(RenterProfileNotFoundException::new);

        if (renterRepository.existsByLicenseNoAndIdNot(
                licenseNo,
                renter.getId())) {
            throw new DrivingLicenseAlreadyExistsException();
        }

        boolean changed =
                !Objects.equals(renter.getLicenseNo(), licenseNo)
                        || !Objects.equals(renter.getLicenseClass(), licenseClass)
                        || !Objects.equals(
                        renter.getLicenseExpiry(),
                        request.licenseExpiry());

        if (changed) {
            renter.setLicenseNo(licenseNo);
            renter.setLicenseClass(licenseClass);
            renter.setLicenseExpiry(request.licenseExpiry());
            renter.setLicenseVerified(false);
            renterRepository.flush();
        }

        return toResponse(renter);
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
                || request.licenseExpiry().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Invalid driving license information");
        }
    }

    private DrivingLicenseResponse toResponse(Renter renter) {
        return new DrivingLicenseResponse(
                renter.getLicenseNo(),
                renter.getLicenseClass(),
                renter.getLicenseExpiry(),
                renter.isLicenseVerified());
    }
}