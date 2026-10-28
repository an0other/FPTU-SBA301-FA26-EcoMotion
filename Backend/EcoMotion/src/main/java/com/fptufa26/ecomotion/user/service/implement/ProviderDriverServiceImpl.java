package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.user.dto.request.CreateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.request.ToggleDriverStatusRequest;
import com.fptufa26.ecomotion.user.dto.request.UpdateDriverByProviderRequest;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverItemResponse;
import com.fptufa26.ecomotion.user.dto.response.ProviderDriverStatsResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.ProviderDriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProviderDriverServiceImpl implements ProviderDriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<ProviderDriverItemResponse> getDrivers(Long providerUserId, String search, DriverStatus status, String sort) {
        Provider provider = getProviderByUserId(providerUserId);
        List<Driver> drivers = driverRepository.findByProvider_IdWithUser(provider.getId());

        String cleanSearch = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";

        return drivers.stream()
                .filter(d -> status == null || d.getStatus() == status)
                .filter(d -> {
                    if (cleanSearch.isEmpty()) {
                        return true;
                    }
                    User u = d.getUser();
                    String name = u != null && u.getFullName() != null ? u.getFullName().toLowerCase(Locale.ROOT) : "";
                    String email = u != null && u.getEmail() != null ? u.getEmail().toLowerCase(Locale.ROOT) : "";
                    String phone = u != null && u.getPhone() != null ? u.getPhone().toLowerCase(Locale.ROOT) : "";
                    String licenseNo = d.getLicenseNo() != null ? d.getLicenseNo().toLowerCase(Locale.ROOT) : "";
                    return name.contains(cleanSearch) || email.contains(cleanSearch) || phone.contains(cleanSearch) || licenseNo.contains(cleanSearch);
                })
                .sorted(getDriverComparator(sort))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderDriverStatsResponse getDriverStats(Long providerUserId) {
        Provider provider = getProviderByUserId(providerUserId);
        Long providerId = provider.getId();

        long total = driverRepository.countByProvider_Id(providerId);
        long approved = driverRepository.countByProvider_IdAndStatus(providerId, DriverStatus.APPROVED);
        long pending = driverRepository.countByProvider_IdAndStatus(providerId, DriverStatus.PENDING);
        long suspended = driverRepository.countByProvider_IdAndStatus(providerId, DriverStatus.SUSPENDED);
        long rejected = driverRepository.countByProvider_IdAndStatus(providerId, DriverStatus.REJECTED);

        return new ProviderDriverStatsResponse(total, approved, pending, suspended, rejected);
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderDriverItemResponse getDriverById(Long providerUserId, Long driverId) {
        Provider provider = getProviderByUserId(providerUserId);
        Driver driver = driverRepository.findByIdAndProvider_Id(driverId, provider.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin tài xế trong đội xe của bạn"));
        return toResponse(driver);
    }

    @Override
    @Transactional
    public ProviderDriverItemResponse createDriver(Long providerUserId, CreateDriverByProviderRequest request) {
        Provider provider = getProviderByUserId(providerUserId);

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email này đã được sử dụng trên hệ thống");
        }

        String normalizedPhone = request.phone().trim();
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new ConflictException("Số điện thoại này đã được sử dụng trên hệ thống");
        }

        String normalizedLicenseNo = request.licenseNo().trim();
        if (driverRepository.existsByLicenseNoIgnoreCase(normalizedLicenseNo)) {
            throw new ConflictException("Số giấy phép lái xe này đã tồn tại trên hệ thống");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setRole(Role.DRIVER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEkycStatus(EkycStatus.VERIFIED);

        String rawPassword = (request.initialPassword() != null && !request.initialPassword().isBlank())
                ? request.initialPassword().trim()
                : "EcoMotion@123";
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        user.setAvatarUrl(request.portraitUrl().trim());

        User savedUser = userRepository.save(user);

        Driver driver = new Driver();
        driver.setUser(savedUser);
        driver.setProvider(provider);
        driver.setLicenseNo(normalizedLicenseNo);
        driver.setLicenseClass(request.licenseClass().trim().toUpperCase(Locale.ROOT));
        driver.setLicenseExpiry(request.licenseExpiry());
        driver.setPortraitUrl(request.portraitUrl().trim());
        driver.setLicenseImageUrl(request.licenseImageUrl().trim());
        driver.setStatus(DriverStatus.PENDING);
        driver.setRatingAvg(BigDecimal.ZERO);
        driver.setRatingCount(0);

        Driver savedDriver = driverRepository.save(driver);
        return toResponse(savedDriver);
    }

    @Override
    @Transactional
    public ProviderDriverItemResponse updateDriver(Long providerUserId, Long driverId, UpdateDriverByProviderRequest request) {
        Provider provider = getProviderByUserId(providerUserId);
        Driver driver = driverRepository.findByIdAndProvider_Id(driverId, provider.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin tài xế trong đội xe của bạn"));

        User user = driver.getUser();

        String normalizedPhone = request.phone().trim();
        if (!normalizedPhone.equals(user.getPhone()) && userRepository.existsByPhone(normalizedPhone)) {
            throw new ConflictException("Số điện thoại mới đã được sử dụng trên hệ thống");
        }

        String normalizedLicenseNo = request.licenseNo().trim();
        if (!normalizedLicenseNo.equalsIgnoreCase(driver.getLicenseNo()) && driverRepository.existsByLicenseNoIgnoreCase(normalizedLicenseNo)) {
            throw new ConflictException("Số GPLX mới đã tồn tại trên hệ thống");
        }

        String newPortraitUrl = request.portraitUrl().trim();
        String newLicenseUrl = request.licenseImageUrl().trim();

        boolean sensitiveChanged = !normalizedLicenseNo.equalsIgnoreCase(driver.getLicenseNo())
                || !request.licenseClass().trim().equalsIgnoreCase(driver.getLicenseClass())
                || !request.licenseExpiry().isEqual(driver.getLicenseExpiry())
                || !newLicenseUrl.equals(driver.getLicenseImageUrl())
                || !newPortraitUrl.equals(driver.getPortraitUrl());

        user.setFullName(request.fullName().trim());
        user.setPhone(normalizedPhone);
        user.setAvatarUrl(newPortraitUrl);
        userRepository.save(user);

        driver.setLicenseNo(normalizedLicenseNo);
        driver.setLicenseClass(request.licenseClass().trim().toUpperCase(Locale.ROOT));
        driver.setLicenseExpiry(request.licenseExpiry());
        driver.setPortraitUrl(newPortraitUrl);
        driver.setLicenseImageUrl(newLicenseUrl);

        if (driver.getStatus() == DriverStatus.APPROVED && sensitiveChanged) {
            driver.setStatus(DriverStatus.PENDING);
        } else if (driver.getStatus() == DriverStatus.REJECTED) {
            driver.setStatus(DriverStatus.PENDING);
            driver.setRejectReason(null);
        }

        Driver updatedDriver = driverRepository.save(driver);
        return toResponse(updatedDriver);
    }

    @Override
    @Transactional
    public ProviderDriverItemResponse toggleDriverStatus(Long providerUserId, Long driverId, ToggleDriverStatusRequest request) {
        Provider provider = getProviderByUserId(providerUserId);
        Driver driver = driverRepository.findByIdAndProvider_Id(driverId, provider.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin tài xế trong đội xe của bạn"));

        DriverStatus targetStatus = request.status();
        if (targetStatus != DriverStatus.APPROVED && targetStatus != DriverStatus.SUSPENDED) {
            throw new BadRequestException("Chỉ được phép chuyển đổi giữa trạng thái Hoạt động (APPROVED) và Tạm ngưng (SUSPENDED)");
        }

        if (driver.getStatus() == DriverStatus.PENDING || driver.getStatus() == DriverStatus.REJECTED) {
            throw new BadRequestException("Tài xế đang ở trạng thái Chờ duyệt hoặc Bị từ chối không thể thao tác tạm ngưng / mở lại");
        }

        driver.setStatus(targetStatus);
        Driver savedDriver = driverRepository.save(driver);
        return toResponse(savedDriver);
    }

    private Provider getProviderByUserId(Long userId) {
        return providerRepository.findByUser_Id(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ Nhà xe tương ứng với tài khoản này"));
    }

    private Comparator<Driver> getDriverComparator(String sort) {
        if ("rating".equalsIgnoreCase(sort)) {
            return Comparator.comparing(Driver::getRatingAvg, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Driver::getRatingCount, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("license_expiry".equalsIgnoreCase(sort) || "expiry".equalsIgnoreCase(sort)) {
            return Comparator.comparing(Driver::getLicenseExpiry, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return Comparator.comparing(Driver::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private ProviderDriverItemResponse toResponse(Driver d) {
        User u = d.getUser();
        return new ProviderDriverItemResponse(
                d.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getFullName() : "",
                u != null ? u.getEmail() : "",
                u != null ? u.getPhone() : "",
                u != null ? u.getAvatarUrl() : "",
                d.getLicenseNo(),
                d.getLicenseClass(),
                d.getLicenseExpiry(),
                d.getPortraitUrl(),
                d.getLicenseImageUrl(),
                d.getStatus(),
                d.getRejectReason(),
                d.getRatingAvg() != null ? d.getRatingAvg() : BigDecimal.ZERO,
                d.getRatingCount() != null ? d.getRatingCount() : 0,
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
