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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_LABEL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UserService userService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final DisputeService disputeService;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return getStats(null, null, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats(Integer year, Integer month) {
        return getStats(year, month, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats(Integer year, Integer month, LocalDate fromDate, LocalDate toDate) {
        BigDecimal platformCommission;
        BigDecimal grossCompleted;
        BigDecimal providerPayoutDue;
        BigDecimal escrowDeposits;
        BigDecimal gmv;
        long completedBookings;
        long totalBookings;

        BigDecimal commissionGrowthRate = null;
        BigDecimal gmvGrowthRate = null;
        BigDecimal payoutGrowthRate = null;
        BigDecimal bookingsGrowthRate = null;
        BigDecimal commissionDiff = null;
        BigDecimal gmvDiff = null;
        BigDecimal payoutDiff = null;
        Long bookingsDiff = null;
        String comparedToLabel = null;
        String selectedFromDateStr = null;
        String selectedToDateStr = null;

        if (fromDate != null || toDate != null) {
            LocalDate start = fromDate != null ? fromDate : toDate;
            LocalDate end = toDate != null ? toDate : fromDate;
            if (start.isAfter(end)) {
                LocalDate tmp = start;
                start = end;
                end = tmp;
            }
            selectedFromDateStr = start.toString();
            selectedToDateStr = end.toString();

            Instant from = start.atStartOfDay(ZONE_VN).toInstant();
            Instant to = end.plusDays(1).atStartOfDay(ZONE_VN).toInstant();

            platformCommission = bookingService.sumAdminCommission(from, to);
            grossCompleted = bookingService.sumCompletedGrossRentalVolume(from, to);
            providerPayoutDue = grossCompleted.subtract(platformCommission).max(BigDecimal.ZERO);
            escrowDeposits = bookingService.sumEscrowDeposits(from, to);
            if (escrowDeposits == null) {
                escrowDeposits = BigDecimal.ZERO;
            }
            // Keep GMV aligned with completed bookings across KPI cards, charts, and monthly summaries
            // rather than falling back to total paid-in cash when completed gross volume is 0.
            gmv = grossCompleted;
            completedBookings = bookingService.countCompletedBookings(from, to);
            totalBookings = bookingService.countTotalBookings(from, to);

            long spanDays = Math.max(ChronoUnit.DAYS.between(start, end) + 1, 1);
            LocalDate prevStart = start.minusDays(spanDays);
            LocalDate prevEnd = start.minusDays(1);
            Instant prevFrom = prevStart.atStartOfDay(ZONE_VN).toInstant();
            Instant prevTo = prevEnd.plusDays(1).atStartOfDay(ZONE_VN).toInstant();

            BigDecimal prevCommission = bookingService.sumAdminCommission(prevFrom, prevTo);
            BigDecimal prevGross = bookingService.sumCompletedGrossRentalVolume(prevFrom, prevTo);
            BigDecimal prevPayout = prevGross.subtract(prevCommission).max(BigDecimal.ZERO);
            long prevCompleted = bookingService.countCompletedBookings(prevFrom, prevTo);

            comparedToLabel = spanDays == 1
                    ? prevStart.format(DATE_LABEL_FMT)
                    : String.format("%s - %s", prevStart.format(DATE_LABEL_FMT), prevEnd.format(DATE_LABEL_FMT));
            commissionDiff = platformCommission.subtract(prevCommission);
            commissionGrowthRate = calculateGrowthRate(platformCommission, prevCommission);

            gmvDiff = gmv.subtract(prevGross);
            gmvGrowthRate = calculateGrowthRate(gmv, prevGross);

            payoutDiff = providerPayoutDue.subtract(prevPayout);
            payoutGrowthRate = calculateGrowthRate(providerPayoutDue, prevPayout);

            bookingsDiff = completedBookings - prevCompleted;
            bookingsGrowthRate = calculateGrowthRate(
                    BigDecimal.valueOf(completedBookings),
                    BigDecimal.valueOf(prevCompleted));
        } else if (year != null && month != null && month >= 1 && month <= 12) {
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
            // Keep GMV aligned with completed bookings across KPI cards, charts, and monthly summaries
            // rather than falling back to total paid-in cash when completed gross volume is 0.
            gmv = grossCompleted;
            completedBookings = bookingService.countCompletedBookings(from, to);
            totalBookings = bookingService.countTotalBookings(from, to);

            // Previous month metrics for MoM comparison
            YearMonth prevYm = ym.minusMonths(1);
            Instant prevFrom = prevYm.atDay(1).atStartOfDay(ZONE_VN).toInstant();
            Instant prevTo = prevYm.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

            BigDecimal prevCommission = bookingService.sumAdminCommission(prevFrom, prevTo);
            BigDecimal prevGross = bookingService.sumCompletedGrossRentalVolume(prevFrom, prevTo);
            BigDecimal prevPayout = prevGross.subtract(prevCommission).max(BigDecimal.ZERO);
            long prevCompleted = bookingService.countCompletedBookings(prevFrom, prevTo);

            comparedToLabel = String.format("Tháng %02d/%d", prevYm.getMonthValue(), prevYm.getYear());
            commissionDiff = platformCommission.subtract(prevCommission);
            commissionGrowthRate = calculateGrowthRate(platformCommission, prevCommission);

            gmvDiff = gmv.subtract(prevGross);
            gmvGrowthRate = calculateGrowthRate(gmv, prevGross);

            payoutDiff = providerPayoutDue.subtract(prevPayout);
            payoutGrowthRate = calculateGrowthRate(providerPayoutDue, prevPayout);

            bookingsDiff = completedBookings - prevCompleted;
            bookingsGrowthRate = calculateGrowthRate(
                    BigDecimal.valueOf(completedBookings),
                    BigDecimal.valueOf(prevCompleted));
        } else {
            platformCommission = bookingService.sumAdminCommission();
            grossCompleted = bookingService.sumCompletedGrossRentalVolume();
            providerPayoutDue = grossCompleted.subtract(platformCommission).max(BigDecimal.ZERO);
            escrowDeposits = bookingService.sumEscrowDeposits();
            if (escrowDeposits == null) {
                escrowDeposits = BigDecimal.ZERO;
            }
            // Keep GMV aligned with completed bookings in all-time stats as well.
            gmv = grossCompleted;
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
                .totalUsers(userService.countUsersByStatus(UserStatus.ACTIVE)
                        + userService.countUsersByStatus(UserStatus.PENDING)
                        + userService.countUsersByStatus(UserStatus.LOCKED))
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
                .selectedFromDate(selectedFromDateStr)
                .selectedToDate(selectedToDateStr)
                .monthlySummaries(monthlySummaries)
                .commissionGrowthRate(commissionGrowthRate)
                .gmvGrowthRate(gmvGrowthRate)
                .payoutGrowthRate(payoutGrowthRate)
                .bookingsGrowthRate(bookingsGrowthRate)
                .commissionDiff(commissionDiff)
                .gmvDiff(gmvDiff)
                .payoutDiff(payoutDiff)
                .bookingsDiff(bookingsDiff)
                .comparedToLabel(comparedToLabel)
                .build();
    }

    private List<MonthlyFinancialSummaryDto> buildMonthlySummaries() {
        YearMonth currentYm = YearMonth.now(ZONE_VN);
        Instant earliestDate = bookingService.findEarliestCompletedBookingDate();
        YearMonth earliestYm = (earliestDate != null)
                ? YearMonth.from(earliestDate.atZone(ZONE_VN))
                : currentYm;
        if (earliestYm.isAfter(currentYm)) {
            earliestYm = currentYm;
        }

        int count = Math.max((int) ChronoUnit.MONTHS.between(earliestYm, currentYm) + 1, 12);
        List<MonthlyFinancialSummaryDto> temp = new ArrayList<>();

        // Build count + 1 months to allow comparing the earliest month (temp.get(count - 1))
        // against its prior month (temp.get(count)) for MoM metrics
        for (int i = 0; i < count + 1; i++) {
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

            temp.add(MonthlyFinancialSummaryDto.builder()
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

        // Calculate MoM growth for each month compared to previous month (temp.get(i + 1))
        for (int i = 0; i < count; i++) {
            MonthlyFinancialSummaryDto curr = temp.get(i);
            MonthlyFinancialSummaryDto prev = temp.get(i + 1);

            curr.setComparedToLabel(prev.getLabel());
            curr.setCommissionDiff(curr.getPlatformCommission().subtract(prev.getPlatformCommission()));
            curr.setCommissionGrowthRate(
                    calculateGrowthRate(curr.getPlatformCommission(), prev.getPlatformCommission()));

            curr.setGmvDiff(curr.getGrossMerchandiseValue().subtract(prev.getGrossMerchandiseValue()));
            curr.setGmvGrowthRate(
                    calculateGrowthRate(curr.getGrossMerchandiseValue(), prev.getGrossMerchandiseValue()));

            curr.setPayoutDiff(curr.getProviderPayoutDue().subtract(prev.getProviderPayoutDue()));
            curr.setPayoutGrowthRate(calculateGrowthRate(curr.getProviderPayoutDue(), prev.getProviderPayoutDue()));

            curr.setBookingsDiff(curr.getCompletedBookings() - prev.getCompletedBookings());
            curr.setBookingsGrowthRate(calculateGrowthRate(
                    BigDecimal.valueOf(curr.getCompletedBookings()),
                    BigDecimal.valueOf(prev.getCompletedBookings())));
        }

        return new ArrayList<>(temp.subList(0, count));
    }

    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current == null || current.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
            }
            return null; // Baseline month with no prior comparison base
        }
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        BigDecimal diff = current.subtract(previous);
        return diff.multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }
}
