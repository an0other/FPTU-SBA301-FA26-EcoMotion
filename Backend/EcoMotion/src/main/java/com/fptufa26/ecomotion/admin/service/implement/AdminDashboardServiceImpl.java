package com.fptufa26.ecomotion.admin.service.implement;

import com.fptufa26.ecomotion.admin.dto.response.AdminStatsResponse;
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
        BigDecimal gmv = bookingService.sumTotalRevenue();
        BigDecimal platformCommission = bookingService.sumAdminCommission();
        BigDecimal grossCompleted = bookingService.sumCompletedGrossRentalVolume();
        BigDecimal providerPayoutDue = grossCompleted.subtract(platformCommission).max(BigDecimal.ZERO);
        BigDecimal escrowDeposits = bookingService.sumEscrowDeposits();
        long completedBookings = bookingService.countBookingsByStatus(BookingStatus.COMPLETED);

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
                .totalRevenue(gmv)
                .grossMerchandiseValue(gmv)
                .platformCommission(platformCommission)
                .providerPayoutDue(providerPayoutDue)
                .escrowDeposits(escrowDeposits)
                .completedBookings(completedBookings)
                .build();
    }
}
