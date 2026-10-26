package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleLicensePlateHistoryResponse;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleLicensePlateHistory;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.VehicleLicensePlateHistoryRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.LicensePlateHistoryService;
import com.fptufa26.ecomotion.vehicle.service.LicensePlatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LicensePlateHistoryServiceImpl implements LicensePlateHistoryService {
    private final VehicleLicensePlateHistoryRepository repository;
    private final VehicleRepository vehicleRepository;
    private final UserService userService;

    @Override
    @Transactional
    public void recordChange(Vehicle vehicle, String previousPlate, String newPlate, User changedBy) {
        VehicleLicensePlateHistory history = new VehicleLicensePlateHistory();
        history.setVehicle(vehicle);
        history.setPreviousPlate(LicensePlatePolicy.canonical(previousPlate));
        history.setNewPlate(LicensePlatePolicy.canonical(newPlate));
        history.setChangedBy(changedBy);
        repository.save(history);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleLicensePlateHistoryResponse> listForProvider(Long userId, Long vehicleId) {
        Long providerId = userService.findProviderByUserId(userId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Tài khoản không thuộc provider"))
                .getId();
        vehicleRepository.findByIdAndProvider_Id(vehicleId, providerId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        return repository.findByVehicle_IdOrderByChangedAtDesc(vehicleId).stream()
                .map(row -> new VehicleLicensePlateHistoryResponse(
                        row.getId(), row.getPreviousPlate(), row.getNewPlate(),
                        row.getChangedBy().getFullName(), row.getChangedAt()))
                .toList();
    }
}
