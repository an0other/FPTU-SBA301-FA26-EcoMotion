package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderVehicleServiceImpl implements ProviderVehicleService {

    private final UserService userService;
    private final VehicleRepository vehicleRepository;
    private final ProviderScheduleService providerScheduleService;

    @Override
    @Transactional(readOnly = true)
    public ProviderDashboardSummaryResponse getDashboardSummary(Long userId) {
        Provider provider = userService.findProviderByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Provider profile not found for user: " + userId));

        Long providerId = provider.getId();
        long totalVehicles = vehicleRepository.countByProvider_Id(providerId);
        long pendingVehicles = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.PENDING);
        long approvedVehicles = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.APPROVED);
        long hiddenVehicles = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.HIDDEN);
        long totalDrivers = userService.countDriversByProviderId(providerId);
        long activeBlocks = providerScheduleService.countActiveBlocksByProviderId(providerId);

        return new ProviderDashboardSummaryResponse(
                provider.getDisplayName(),
                provider.getDepotAddress(),
                provider.getHotline(),
                totalVehicles,
                pendingVehicles,
                approvedVehicles,
                hiddenVehicles,
                totalDrivers,
                activeBlocks
        );
    }
}
