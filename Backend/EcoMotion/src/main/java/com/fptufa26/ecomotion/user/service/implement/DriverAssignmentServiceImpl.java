package com.fptufa26.ecomotion.user.service.implement;

import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.DriverActivityStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.service.DriverAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverAssignmentServiceImpl implements DriverAssignmentService {

    private final DriverRepository driverRepository;
    private final EntityManager entityManager;

    @Override
    public List<Driver> listEligible(Long providerId, LocalDate serviceDate) {
        return driverRepository.findEligibleDrivers(providerId, DriverStatus.APPROVED, serviceDate).stream()
                .filter(driver -> driver.getUser() != null && driver.getUser().getStatus() == UserStatus.ACTIVE)
                .filter(driver -> driver.getActivityStatus() == null || driver.getActivityStatus() == DriverActivityStatus.AVAILABLE)
                .toList();
    }

    @Override
    @Transactional
    public Driver requireEligibleForUpdate(Long providerId, Long driverId, LocalDate serviceDate) {
        Driver driver = driverRepository.findEligibleByIdAndProviderForUpdate(
                        driverId, providerId, DriverStatus.APPROVED, serviceDate)
                .orElseThrow(() -> new NotFoundException(
                        "Tài xế không tồn tại, chưa được duyệt hoặc giấy phép đã hết hạn"));
        entityManager.refresh(driver, LockModeType.PESSIMISTIC_WRITE);
        if (driver.getUser() == null || driver.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Tài khoản tài xế không ở trạng thái hoạt động");
        }
        if (driver.getActivityStatus() != null && driver.getActivityStatus() != DriverActivityStatus.AVAILABLE) {
            throw new ConflictException("Tài xế đang tạm nghỉ hoặc ngoại tuyến");
        }
        return driver;
    }
}
