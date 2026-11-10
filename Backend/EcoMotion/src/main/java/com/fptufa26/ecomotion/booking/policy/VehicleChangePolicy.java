package com.fptufa26.ecomotion.booking.policy;

import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Quy tắc B1: chênh lệch giá và chất lượng khi nhà xe đề xuất đổi xe. Không truy cập DB. */
public final class VehicleChangePolicy {
    private VehicleChangePolicy() {}

    /** Khoảng thời gian áp dụng một xe với giá thuê của xe đó cho toàn kỳ. [from, to) theo ngày. */
    public record Segment(BigDecimal fullPrice, LocalDate from, LocalDate to) {}

    /** Lý do xe mới có chất lượng thấp hơn xe cũ; rỗng nếu không thấp hơn. */
    public static List<String> qualityDowngrades(Vehicle current, Vehicle replacement) {
        List<String> notes = new ArrayList<>();
        if (current.getSeatCount() != null && replacement.getSeatCount() != null
                && replacement.getSeatCount() < current.getSeatCount()) {
            notes.add("Số chỗ ngồi giảm từ " + current.getSeatCount() + " xuống " + replacement.getSeatCount());
        }
        if (current.getManufactureYear() != null && replacement.getManufactureYear() != null
                && replacement.getManufactureYear() < current.getManufactureYear()) {
            notes.add("Đời xe cũ hơn (" + replacement.getManufactureYear() + " so với " + current.getManufactureYear() + ")");
        }
        return notes;
    }

    /** Xe mới đắt hơn hoặc bằng thì không phát sinh gì; rẻ hơn thì giảm trừ phần chênh. */
    public static BigDecimal discount(BigDecimal contractPrice, BigDecimal replacementPrice) {
        if (contractPrice == null || replacementPrice == null) return BigDecimal.ZERO;
        return contractPrice.subtract(replacementPrice).max(BigDecimal.ZERO);
    }

    /**
     * Giảm trừ gói năm theo số ngày thực tế dùng từng xe. Mỗi đoạn so với giá hợp đồng gốc nên
     * chuỗi nhiều lần đổi không cộng dồn lặp; xe đắt hơn đóng góp 0.
     */
    public static BigDecimal yearlyDiscount(BigDecimal contractTotal, LocalDate contractStart, LocalDate contractEnd,
                                            List<Segment> segments) {
        long contractDays = ChronoUnit.DAYS.between(contractStart, contractEnd);
        if (contractTotal == null || contractDays <= 0) return BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (Segment s : segments) {
            LocalDate from = s.from().isBefore(contractStart) ? contractStart : s.from();
            LocalDate to = s.to().isAfter(contractEnd) ? contractEnd : s.to();
            long days = ChronoUnit.DAYS.between(from, to);
            if (days <= 0) continue;
            weighted = weighted.add(discount(contractTotal, s.fullPrice()).multiply(BigDecimal.valueOf(days)));
        }
        return weighted.divide(BigDecimal.valueOf(contractDays), 0, RoundingMode.HALF_UP).min(contractTotal);
    }
}
