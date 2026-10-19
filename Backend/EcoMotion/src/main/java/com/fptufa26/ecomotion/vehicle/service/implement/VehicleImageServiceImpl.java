package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleImageServiceImpl implements VehicleImageService {

    private final VehicleImageRepository vehicleImageRepository;

    @Override
    public List<String> findImageUrls(Long vehicleId) {
        return vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicleId).stream()
                .map(VehicleImage::getImageUrl)
                .toList();
    }

    @Override
    public Map<Long, List<String>> findImageUrlsByVehicleIds(Collection<Long> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        return vehicleImageRepository.findByVehicle_IdInOrderByDisplayOrderAsc(vehicleIds).stream()
                .collect(Collectors.groupingBy(image -> image.getVehicle().getId(),
                        Collectors.mapping(VehicleImage::getImageUrl, Collectors.toList())));
    }
}
