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

    public static final LocalDate CURRENT_CLASS_EFFECTIVE_DATE = LocalDate.of(2025, 1, 1);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private DrivingLicenseEligibilityPolicy() {
    }

    public static List<DrivingLicenseClassOptionResponse> supportedClasses() {
        return List.of(
                new DrivingLicenseClassOptionResponse(
                        "B",
                        "B — ô tô chở người đến 8 chỗ (không kể chỗ người lái)",
                        CURRENT_CLASS_EFFECTIVE_DATE,
                        true,
                        "Chính sách demo chỉ tự động hỗ trợ GPLX hạng B cấp từ ngày 01/01/2025. Chưa đối chiếu khối lượng xe."),
                new DrivingLicenseClassOptionResponse(
                        "B1/B2 (cũ)",
                        "B1/B2 — hạng GPLX cũ",
                        null,
                        false,
                        "GPLX cũ vẫn được sử dụng theo thời hạn ghi trên thẻ; hệ thống demo chưa tự động xử lý hạn chế và điều kiện chuyển đổi."));
    }

    public static boolean isEligibleForYearlyRental(
            String licenseClass,
            LocalDate issueDate,
            LocalDate expiryDate,
            boolean expiryUnlimited) {
        return licenseClass != null
                && "B".equals(licenseClass.trim().toUpperCase(Locale.ROOT))
                && issueDate != null
                && !issueDate.isBefore(CURRENT_CLASS_EFFECTIVE_DATE)
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
        if (!"B".equals(licenseClass.trim().toUpperCase(Locale.ROOT))) {
            // return "Bản demo chỉ tự động hỗ trợ GPLX hạng B hiện hành. GPLX hạng cũ cần được đối chiếu riêng.";
            return "";
        }
        if (issueDate == null || issueDate.isBefore(CURRENT_CLASS_EFFECTIVE_DATE)) {
            return "Hạng B hiện hành có hiệu lực từ 01/01/2025; vui lòng kiểm tra ngày cấp hoặc đổi/cấp lại trên GPLX.";
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
                    "Cần tải đủ hai mặt GPLX và hoàn tất kiểm tra OCR/QR trước khi đặt gói năm.");
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
