package com.fptufa26.ecomotion.admin.service;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;

public interface AdminDashboardService {
    AdminStatsResponse getStats();
    AdminStatsResponse getStats(Integer year, Integer month);
}
