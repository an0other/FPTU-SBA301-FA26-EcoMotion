package com.fptufa26.ecomotion.schedule.service.implement;

import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderScheduleServiceImpl implements ProviderScheduleService {

    private final VehicleScheduleRepository vehicleScheduleRepository;

    @Override
    @Transactional(readOnly = true)
    public long countActiveBlocksByProviderId(Long providerId) {
        if (providerId == null) {
            return 0;
        }
        return vehicleScheduleRepository.countActiveBlocksByProviderId(providerId);
    }
}
