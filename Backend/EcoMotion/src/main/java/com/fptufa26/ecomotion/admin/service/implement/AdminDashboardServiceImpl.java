package com.fptufa26.ecomotion.admin.service.implement;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;
import com.fptufa26.ecomotion.admin.dto.response.MonthlyFinancialSummaryDto;
import com.fptufa26.ecomotion.admin.service.AdminDashboardService;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserService userService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final DisputeService disputeService;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return getStats(null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats(Integer year, Integer month) {
        BigDecimal platformCommission;
        BigDecimal grossCompleted;
        BigDecimal providerPayoutDue;
        BigDecimal escrowDeposits;
        BigDecimal gmv;
        long completedBookings;
        long totalBookings;

        if (year != null && month != null && month >= 1 && month <= 12) {
            YearMonth ym = YearMonth.of(year, month);
            Instant from = ym.atDay(1).atStartOfDay(ZONE_VN).toInstant();
            Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

            platformCommission = bookingService.sumAdminCommission(from, to);
            grossCompleted = bookingService.sumCompletedGrossRentalVolume(from, to);
            providerPayoutDue = grossCompleted.subtract(platformCommission).max(BigDecimal.ZERO);
            escrowDeposits = bookingService.sumEscrowDeposits(from, to);
            if (escrowDeposits == null) {
                escrowDeposits = BigDecimal.ZERO;
            }
            gmv = grossCompleted.compareTo(BigDecimal.ZERO) > 0 ? grossCompleted : bookingService.sumTotalRevenue(from, to);
            completedBookings = bookingService.countCompletedBookings(from, to);
            totalBookings = bookingService.countTotalBookings(from, to);
        } else {
            platformCommission = bookingService.sumAdminCommission();
            grossCompleted = bookingService.sumCompletedGrossRentalVolume();
            providerPayoutDue = grossCompleted.subtract(platformCommission).max(BigDecimal.ZERO);
            escrowDeposits = bookingService.sumEscrowDeposits();
            if (escrowDeposits == null) {
                escrowDeposits = BigDecimal.ZERO;
            }
            gmv = grossCompleted.compareTo(BigDecimal.ZERO) > 0 ? grossCompleted : bookingService.sumTotalRevenue();
            completedBookings = bookingService.countBookingsByStatus(BookingStatus.COMPLETED);
            totalBookings = bookingService.countTotalBookings();
        }

        List<MonthlyFinancialSummaryDto> monthlySummaries = buildMonthlySummaries();

        return AdminStatsResponse.builder()
                .pendingProviders(userService.countPendingProviders())
                .pendingDrivers(userService.countPendingDrivers())
                .pendingEkyc(userService.countPendingEkyc())
                .pendingVehicles(vehicleService.countVehiclesByStatus(VehicleStatus.PENDING))
                .activeUsers(userService.countUsersByStatus(UserStatus.ACTIVE))
                .totalUsers(userService.countUsersByStatus(UserStatus.ACTIVE) + userService.countUsersByStatus(UserStatus.PENDING) + userService.countUsersByStatus(UserStatus.LOCKED))
                .approvedVehicles(vehicleService.countVehiclesByStatus(VehicleStatus.APPROVED))
                .totalVehicles(vehicleService.countTotalVehicles())
                .totalBookings(totalBookings)
                .openDisputes(disputeService.countOpenOrInReview())
                .totalRevenue(gmv)
                .grossMerchandiseValue(gmv)
                .platformCommission(platformCommission)
                .providerPayoutDue(providerPayoutDue)
                .escrowDeposits(escrowDeposits)
                .completedBookings(completedBookings)
                .selectedYear(year)
                .selectedMonth(month)
                .monthlySummaries(monthlySummaries)
                .build();
    }

    private List<MonthlyFinancialSummaryDto> buildMonthlySummaries() {
        List<MonthlyFinancialSummaryDto> list = new ArrayList<>();
        YearMonth currentYm = YearMonth.now(ZONE_VN);
        // Build last 6 months (including current month)
        for (int i = 0; i < 6; i++) {
            YearMonth ym = currentYm.minusMonths(i);
            Instant from = ym.atDay(1).atStartOfDay(ZONE_VN).toInstant();
            Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

            BigDecimal commission = bookingService.sumAdminCommission(from, to);
            BigDecimal gross = bookingService.sumCompletedGrossRentalVolume(from, to);
            BigDecimal payout = gross.subtract(commission).max(BigDecimal.ZERO);
            BigDecimal escrow = bookingService.sumEscrowDeposits(from, to);
            if (escrow == null) {
                escrow = BigDecimal.ZERO;
            }
            long completed = bookingService.countCompletedBookings(from, to);
            long total = bookingService.countTotalBookings(from, to);

            String label = String.format("Tháng %02d/%d", ym.getMonthValue(), ym.getYear());

            list.add(MonthlyFinancialSummaryDto.builder()
                    .year(ym.getYear())
                    .month(ym.getMonthValue())
                    .label(label)
                    .grossMerchandiseValue(gross)
                    .platformCommission(commission)
                    .providerPayoutDue(payout)
                    .escrowDeposits(escrow)
                    .completedBookings(completed)
                    .totalBookings(total)
                    .build());
        }
        return list;
    }
}
