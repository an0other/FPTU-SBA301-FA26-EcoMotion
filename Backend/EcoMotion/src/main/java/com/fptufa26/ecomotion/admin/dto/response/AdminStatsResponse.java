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
}
