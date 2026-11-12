package com.fptufa26.ecomotion.admin.service.implement;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;
import com.fptufa26.ecomotion.admin.dto.response.MonthlyFinancialSummaryDto;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardServiceImplTest {

    private static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private UserService userService;
    private VehicleService vehicleService;
    private BookingService bookingService;
    private DisputeService disputeService;
    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        vehicleService = mock(VehicleService.class);
        bookingService = mock(BookingService.class);
        disputeService = mock(DisputeService.class);
        service = new AdminDashboardServiceImpl(userService, vehicleService, bookingService, disputeService);

        when(bookingService.sumAdminCommission(any(), any())).thenReturn(BigDecimal.ZERO);
        when(bookingService.sumCompletedGrossRentalVolume(any(), any())).thenReturn(BigDecimal.ZERO);
        when(bookingService.sumEscrowDeposits(any(), any())).thenReturn(BigDecimal.ZERO);
        when(bookingService.countCompletedBookings(any(), any())).thenReturn(0L);
        when(bookingService.countTotalBookings(any(), any())).thenReturn(0L);

        when(bookingService.sumAdminCommission()).thenReturn(BigDecimal.ZERO);
        when(bookingService.sumCompletedGrossRentalVolume()).thenReturn(BigDecimal.ZERO);
        when(bookingService.sumEscrowDeposits()).thenReturn(BigDecimal.ZERO);
        when(bookingService.countBookingsByStatus(BookingStatus.COMPLETED)).thenReturn(0L);
        when(bookingService.countTotalBookings()).thenReturn(0L);
    }

    @Test
    void monthlyFilterWithZeroCompletedTripsIgnoresTotalRevenueFallback() {
        YearMonth nov = YearMonth.of(2026, 11);
        Instant novFrom = nov.atDay(1).atStartOfDay(ZONE_VN).toInstant();
        Instant novTo = nov.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

        YearMonth oct = YearMonth.of(2026, 10);
        Instant octFrom = oct.atDay(1).atStartOfDay(ZONE_VN).toInstant();
        Instant octTo = oct.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

        BigDecimal prevGross = new BigDecimal("24755000");
        BigDecimal prevCommission = new BigDecimal("2475500");

        when(bookingService.sumCompletedGrossRentalVolume(octFrom, octTo)).thenReturn(prevGross);
        when(bookingService.sumAdminCommission(octFrom, octTo)).thenReturn(prevCommission);
        when(bookingService.countCompletedBookings(octFrom, octTo)).thenReturn(15L);

        when(bookingService.sumTotalRevenue(novFrom, novTo)).thenReturn(new BigDecimal("66014000"));

        AdminStatsResponse stats = service.getStats(2026, 11);

        assertThat(stats.getGrossMerchandiseValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getPlatformCommission()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getProviderPayoutDue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getCompletedBookings()).isZero();
        assertThat(stats.getGmvDiff()).isEqualByComparingTo(prevGross.negate());
        assertThat(stats.getGmvGrowthRate()).isEqualByComparingTo(new BigDecimal("-100.0"));
        assertThat(stats.getComparedToLabel()).isEqualTo("Tháng 10/2026");

        verify(bookingService, never()).sumTotalRevenue(any(), any());
    }

    @Test
    void dateRangeFilterWithZeroCompletedTripsIgnoresTotalRevenueFallback() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 12);
        Instant from = start.atStartOfDay(ZONE_VN).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZONE_VN).toInstant();

        long spanDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevStart = start.minusDays(spanDays);
        LocalDate prevEnd = start.minusDays(1);
        Instant prevFrom = prevStart.atStartOfDay(ZONE_VN).toInstant();
        Instant prevTo = prevEnd.plusDays(1).atStartOfDay(ZONE_VN).toInstant();

        BigDecimal prevGross = new BigDecimal("12000000");
        when(bookingService.sumCompletedGrossRentalVolume(prevFrom, prevTo)).thenReturn(prevGross);
        when(bookingService.sumAdminCommission(prevFrom, prevTo)).thenReturn(new BigDecimal("1200000"));
        when(bookingService.countCompletedBookings(prevFrom, prevTo)).thenReturn(4L);
        when(bookingService.sumTotalRevenue(from, to)).thenReturn(new BigDecimal("66014000"));

        AdminStatsResponse stats = service.getStats(null, null, start, end);

        assertThat(stats.getGrossMerchandiseValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getGmvDiff()).isEqualByComparingTo(prevGross.negate());
        assertThat(stats.getGmvGrowthRate()).isEqualByComparingTo(new BigDecimal("-100.0"));

        verify(bookingService, never()).sumTotalRevenue(any(), any());
    }

    @Test
    void allTimeFilterWithZeroCompletedTripsIgnoresTotalRevenueFallback() {
        when(bookingService.sumTotalRevenue()).thenReturn(new BigDecimal("66014000"));

        AdminStatsResponse stats = service.getStats();

        assertThat(stats.getGrossMerchandiseValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(bookingService, never()).sumTotalRevenue();
    }

    @Test
    void kpiCardAndMonthlySummaryRemainConsistentForCurrentMonth() {
        YearMonth currentYm = YearMonth.now(ZONE_VN);
        YearMonth prevYm = currentYm.minusMonths(1);
        Instant prevFrom = prevYm.atDay(1).atStartOfDay(ZONE_VN).toInstant();
        Instant prevTo = prevYm.plusMonths(1).atDay(1).atStartOfDay(ZONE_VN).toInstant();

        BigDecimal prevGross = new BigDecimal("24755000");
        BigDecimal prevCommission = new BigDecimal("2475500");
        when(bookingService.sumCompletedGrossRentalVolume(prevFrom, prevTo)).thenReturn(prevGross);
        when(bookingService.sumAdminCommission(prevFrom, prevTo)).thenReturn(prevCommission);
        when(bookingService.countCompletedBookings(prevFrom, prevTo)).thenReturn(15L);

        when(bookingService.sumTotalRevenue(any(), any())).thenReturn(new BigDecimal("66014000"));

        AdminStatsResponse stats = service.getStats(currentYm.getYear(), currentYm.getMonthValue());
        MonthlyFinancialSummaryDto currentSummary = stats.getMonthlySummaries().get(0);

        assertThat(stats.getGrossMerchandiseValue()).isEqualByComparingTo(currentSummary.getGrossMerchandiseValue());
        assertThat(stats.getPlatformCommission()).isEqualByComparingTo(currentSummary.getPlatformCommission());
        assertThat(stats.getProviderPayoutDue()).isEqualByComparingTo(currentSummary.getProviderPayoutDue());
        assertThat(stats.getCompletedBookings()).isEqualTo(currentSummary.getCompletedBookings());
        assertThat(stats.getGmvDiff()).isEqualByComparingTo(currentSummary.getGmvDiff());
        assertThat(stats.getGmvGrowthRate()).isEqualByComparingTo(currentSummary.getGmvGrowthRate());
    }
}

