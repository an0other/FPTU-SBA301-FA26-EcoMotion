package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.policy.YearlyRentalRules;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.YearlyRentalQuoteService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YearlyRentalQuoteServiceImpl implements YearlyRentalQuoteService {
    private final BookingRepository bookings;
    private final BookingPolicySnapshotRepository snapshots;
    private final BigDecimal roundingUnit;
    private final com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService ledger;

    @org.springframework.beans.factory.annotation.Autowired
    public YearlyRentalQuoteServiceImpl(BookingRepository bookings,
            BookingPolicySnapshotRepository snapshots, com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService ledger,
            @Value("${booking.yearly.rounding-unit:100000}") BigDecimal roundingUnit) {
        if (roundingUnit.signum() <= 0) throw new IllegalArgumentException("Invalid yearly rounding unit");
        this.bookings = bookings;
        this.snapshots = snapshots;
        this.roundingUnit = roundingUnit;
        this.ledger = ledger;
    }

    @Override
    public void validatePrice(BigDecimal total) {
        try {
            YearlyRentalRules.installments(total, roundingUnit);
        } catch (IllegalArgumentException ex) {
            throw new ConflictException("Giá năm không chia được thành 12 tháng theo bước làm tròn đã cấu hình");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Quote quote(Long userId, String bookingCode) {
        var booking = bookings.findByBookingCodeAndRenter_User_Id(bookingCode, userId)
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê"));
        var snapshot = snapshots.findByBooking_Id(booking.getId())
                .orElseThrow(() -> new ConflictException("Thiếu chính sách của đơn thuê"));
        if (snapshot.getPlanType() != PlanType.YEARLY) throw new ConflictException("Chỉ áp dụng cho gói năm");
        var start = (booking.getActualStartAt() != null ? booking.getActualStartAt() : booking.getPickupTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var total = booking.getTotalAmount();
        var effectiveUnit = ledger.yearlyRoundingUnit(booking.getId()).orElse(roundingUnit);
        java.util.List<BigDecimal> rents;
        try {
            rents = YearlyRentalRules.installments(total, effectiveUnit);
        } catch (IllegalArgumentException ex) {
            throw new ConflictException("Giá năm chưa phù hợp với bước chia tiền đã cấu hình");
        }
        var months = new ArrayList<Month>();
        for (int i = 0; i < rents.size(); i++) {
            months.add(new Month(i + 1, YearlyRentalRules.monthBoundary(start, i),
                    YearlyRentalRules.monthBoundary(start, i + 1), rents.get(i)));
        }
        return new Quote(bookingCode, booking.getStatus().name(), start, start.plusMonths(12),
                total, effectiveUnit, total.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                YearlyRentalRules.earlyTerminationFee(total, BigDecimal.ZERO, effectiveUnit),
                java.util.List.copyOf(months), "PREVIEW_ONLY_NOT_PAYMENT_REQUESTS");
    }
}
