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
}

