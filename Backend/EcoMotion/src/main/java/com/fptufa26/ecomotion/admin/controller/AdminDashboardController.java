package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;
import com.fptufa26.ecomotion.admin.service.AdminDashboardService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin - Dashboard", description = "A01: Thống kê và liên kết nhanh cho Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Lấy các số đếm tổng quan và chờ duyệt cho trang chủ Admin (A01)")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        AdminStatsResponse stats = adminDashboardService.getStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
