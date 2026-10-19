package com.fptufa26.ecomotion.admin.service.implement;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;
import com.fptufa26.ecomotion.admin.service.AdminDashboardService;
import com.fptufa26.ecomotion.booking.service.BookingService;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserService userService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;
    private final DisputeService disputeService;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .pendingProviders(userService.countPendingProviders())
                .pendingDrivers(userService.countPendingDrivers())
                .pendingEkyc(userService.countPendingEkyc())
                .pendingVehicles(vehicleService.countVehiclesByStatus(VehicleStatus.PENDING))
                .activeUsers(userService.countUsersByStatus(UserStatus.ACTIVE))
                .totalUsers(userService.countUsersByStatus(UserStatus.ACTIVE) + userService.countUsersByStatus(UserStatus.PENDING) + userService.countUsersByStatus(UserStatus.LOCKED))
                .approvedVehicles(vehicleService.countVehiclesByStatus(VehicleStatus.APPROVED))
                .totalVehicles(vehicleService.countTotalVehicles())
                .totalBookings(bookingService.countTotalBookings())
                .openDisputes(disputeService.countOpenOrInReview())
                .totalRevenue(bookingService.sumTotalRevenue())
                .build();
    }
}
