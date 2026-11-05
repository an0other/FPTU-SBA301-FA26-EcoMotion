package com.fptufa26.ecomotion.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long pendingProviders;
    private long pendingDrivers;
    private long pendingEkyc;
    private long pendingVehicles;
    private long activeUsers;
    private long totalUsers;
    private long approvedVehicles;
    private long totalVehicles;
    private long totalBookings;
    private long openDisputes;
    private BigDecimal totalRevenue;
    private BigDecimal grossMerchandiseValue;
    private BigDecimal platformCommission;
    private BigDecimal providerPayoutDue;
    private BigDecimal escrowDeposits;
    private long completedBookings;
    private Integer selectedYear;
    private Integer selectedMonth;
    private List<MonthlyFinancialSummaryDto> monthlySummaries;

    // Month-over-month (MoM) growth & difference metrics for the selected period
    private BigDecimal commissionGrowthRate;
    private BigDecimal gmvGrowthRate;
    private BigDecimal payoutGrowthRate;
    private BigDecimal bookingsGrowthRate;
    private BigDecimal commissionDiff;
    private BigDecimal gmvDiff;
    private BigDecimal payoutDiff;
    private Long bookingsDiff;
    private String comparedToLabel;
}
