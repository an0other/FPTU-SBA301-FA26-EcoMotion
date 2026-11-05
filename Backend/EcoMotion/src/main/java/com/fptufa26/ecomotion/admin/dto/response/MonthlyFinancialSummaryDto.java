package com.fptufa26.ecomotion.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyFinancialSummaryDto {
    private int year;
    private int month;
    private String label;
    private BigDecimal grossMerchandiseValue;
    private BigDecimal platformCommission;
    private BigDecimal providerPayoutDue;
    private BigDecimal escrowDeposits;
    private long completedBookings;
    private long totalBookings;

    // Month-over-month (MoM) comparison vs previous month
    private BigDecimal commissionGrowthRate; // e.g. 17.0 (%)
    private BigDecimal gmvGrowthRate;        // e.g. 17.0 (%)
    private BigDecimal payoutGrowthRate;     // e.g. 17.0 (%)
    private BigDecimal bookingsGrowthRate;   // e.g. 50.0 (%)
    private BigDecimal commissionDiff;       // e.g. +243,000 VND
    private BigDecimal gmvDiff;              // e.g. +2,430,000 VND
    private BigDecimal payoutDiff;           // e.g. +2,187,000 VND
    private Long bookingsDiff;               // e.g. +4
    private String comparedToLabel;          // e.g. "Tháng 09/2026"
}
