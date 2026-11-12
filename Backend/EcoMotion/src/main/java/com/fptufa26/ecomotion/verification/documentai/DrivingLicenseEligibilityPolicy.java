package com.fptufa26.ecomotion.verification.documentai;

import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.user.dto.response.DrivingLicenseClassOptionResponse;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public final class DrivingLicenseEligibilityPolicy {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private DrivingLicenseEligibilityPolicy() {
    }

    public static List<DrivingLicenseClassOptionResponse> supportedClasses() {
        return List.of("B", "B1", "B2").stream()
                .map(code -> new DrivingLicenseClassOptionResponse(
                        code, code + " — nhóm B trong chính sách demo", null, true,
                        "Demo hỗ trợ B, B1 và B2; không giới hạn ngày cấp từ 2025. Vẫn kiểm tra hạn GPLX, tuổi và số chỗ xe."))
                .toList();
    }

    private static boolean isSupportedClass(String licenseClass) {
        return licenseClass != null
                && List.of("B", "B1", "B2").contains(licenseClass.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isEligibleForYearlyRental(
            String licenseClass,
            LocalDate issueDate,
            LocalDate expiryDate,
            boolean expiryUnlimited) {
        return isSupportedClass(licenseClass)
                && !expiryUnlimited
                && expiryDate != null
                && !expiryDate.isBefore(LocalDate.now(VIETNAM_ZONE));
    }

    public static String ineligibleReason(
            String licenseClass,
            LocalDate issueDate,
            LocalDate expiryDate,
            boolean expiryUnlimited) {
        if (licenseClass == null || licenseClass.isBlank()) {
            return "Chưa đọc được hạng GPLX.";
        }
        if (!isSupportedClass(licenseClass)) {
            return "Chính sách demo hỗ trợ GPLX nhóm B: B, B1 và B2.";
        }
        if (expiryUnlimited || expiryDate == null) {
            return "Chưa đọc được ngày hết hạn để xác nhận GPLX còn hiệu lực trong toàn bộ thời hạn thuê.";
        }
        if (expiryDate.isBefore(LocalDate.now(VIETNAM_ZONE))) {
            return "GPLX đã hết hạn.";
        }
        return null;
    }

    public static void requireEligibleForBooking(
            Renter renter,
            Vehicle vehicle,
            Instant start,
            Instant end) {
        if (vehicle.getSeatCount() > 9) {
            throw new ConflictException(
                    "Bản demo chỉ hỗ trợ xe chở tối đa 8 hành khách ngoài người lái.");
        }
        if (!renter.isLicenseVerified()) {
            throw new ConflictException(
                    "Cần GPLX được Admin chấp nhận trước khi đặt gói năm tự lái.");
        }
        if (!isEligibleForYearlyRental(
                renter.getLicenseClass(),
                renter.getLicenseIssueDate(),
                renter.getLicenseExpiry(),
                renter.isLicenseExpiryUnlimited())) {
            throw new ConflictException(ineligibleReason(
                    renter.getLicenseClass(),
                    renter.getLicenseIssueDate(),
                    renter.getLicenseExpiry(),
                    renter.isLicenseExpiryUnlimited()));
        }
        LocalDate requiredThrough = LocalDate.ofInstant(end, VIETNAM_ZONE);
        if (renter.getLicenseExpiry().isBefore(requiredThrough)) {
            throw new ConflictException(
                    "GPLX phải còn hạn đến hết thời gian thuê năm. Vui lòng gia hạn hoặc chọn thời gian thuê khác.");
        }
        LocalDate pickupDate = LocalDate.ofInstant(start, VIETNAM_ZONE);
        if (renter.getLicenseDateOfBirth() == null
                || renter.getLicenseDateOfBirth().plusYears(18).isAfter(pickupDate)) {
            throw new ConflictException("Người thuê phải đủ 18 tuổi tại ngày bắt đầu thuê.");
        }
    }
}
