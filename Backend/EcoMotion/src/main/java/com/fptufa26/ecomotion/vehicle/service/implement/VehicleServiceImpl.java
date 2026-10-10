package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final RentalPlanRepository rentalPlanRepository;

    @Override
    public PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                                  String fuelType, String planType,
                                                                  BigDecimal maxPrice,
                                                                  VehicleSort sort,
                                                                  int offset, int limit) {
        List<Vehicle> vehicles = vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED);
        if (vehicles.isEmpty()) {
            return PageResponse.of(List.of(), offset, limit);
        }

        List<Long> vehicleIds = vehicles.stream().map(Vehicle::getId).toList();

        Map<Long, List<VehicleImage>> imagesByVehicle = vehicleImageRepository
                .findByVehicle_IdInOrderByDisplayOrderAsc(vehicleIds).stream()
                .collect(Collectors.groupingBy(image -> image.getVehicle().getId()));

        Map<Long, List<RentalPlan>> plansByVehicle = rentalPlanRepository
                .findByVehicle_IdInAndStatusOrderByIdAsc(vehicleIds, PlanStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(plan -> plan.getVehicle().getId()));

        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedLocation = location == null ? null : location.trim().toLowerCase(Locale.ROOT);
        FuelType fuelFilter = parseFuelType(fuelType);
        PlanType planFilter = parsePlanType(planType);

        List<VehicleCardResponse> matching = vehicles.stream()
                .map(vehicle -> toCard(vehicle,
                        imagesByVehicle.getOrDefault(vehicle.getId(), List.of()),
                        plansByVehicle.getOrDefault(vehicle.getId(), List.of())))
                .filter(card -> matchesKeyword(card, normalizedKeyword))
                .filter(card -> matchesLocation(card, normalizedLocation))
                .filter(card -> fuelFilter == null || card.fuelType() == fuelFilter)
                .filter(card -> planFilter == null
                        || card.plans().stream().anyMatch(p -> p.planType() == planFilter))
                .filter(card -> maxPrice == null
                        || planScopedPrice(card, planFilter).compareTo(maxPrice) <= 0)
                .sorted(comparatorFor(sort, planFilter))
                .toList();

        return PageResponse.of(matching, offset, limit);
    }

    private VehicleCardResponse toCard(Vehicle vehicle, List<VehicleImage> images, List<RentalPlan> plans) {
        List<String> imageUrls = images.stream()
                .sorted(Comparator.comparing(VehicleImage::getDisplayOrder))
                .map(VehicleImage::getImageUrl)
                .toList();

        List<VehicleCardResponse.PlanPrice> planPrices = plans.stream()
                .map(plan -> new VehicleCardResponse.PlanPrice(
                        plan.getPlanType(),
                        plan.getSlotLengthMinutes(),
                        plan.getSlotPrice(),
                        plan.getUpfrontAmount()))
                .toList();

        var provider = vehicle.getProvider();
        return new VehicleCardResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getLicensePlate(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getRatingAvg(),
                vehicle.getRatingCount(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                imageUrls,
                provider == null ? null : provider.getId(),
                provider == null ? null : provider.getDisplayName(),
                provider == null ? null : provider.getHotline(),
                provider == null ? null : provider.getDepotAddress(),
                planPrices);
    }

    private BigDecimal cheapestPrice(VehicleCardResponse card) {
        return card.plans().stream()
                .map(VehicleCardResponse.PlanPrice::slotPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
    }

    private BigDecimal planScopedPrice(VehicleCardResponse card, PlanType planFilter) {
        if (planFilter == null) {
            return cheapestPrice(card);
        }
        return card.plans().stream()
                .filter(plan -> plan.planType() == planFilter)
                .map(VehicleCardResponse.PlanPrice::slotPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
    }

    private Comparator<VehicleCardResponse> comparatorFor(VehicleSort sort, PlanType planFilter) {
        VehicleSort effective = sort == null ? VehicleSort.DEFAULT : sort;
        return switch (effective) {
            case PRICE_ASC -> Comparator
                    .comparing((VehicleCardResponse card) -> planScopedPrice(card, planFilter))
                    .thenComparing(VehicleCardResponse::id);
            case PRICE_DESC -> Comparator
                    .comparing((VehicleCardResponse card) -> planScopedPrice(card, planFilter))
                    .reversed()
                    .thenComparing(VehicleCardResponse::id);
            case RATING -> Comparator
                    .comparing((VehicleCardResponse card) -> card.ratingAvg() == null
                            ? BigDecimal.ZERO
                            : card.ratingAvg())
                    .reversed()
                    .thenComparing(VehicleCardResponse::id);
            case DEFAULT -> Comparator.comparing(VehicleCardResponse::id);
        };
    }

    private boolean matchesKeyword(VehicleCardResponse card, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return contains(card.brand(), keyword)
                || contains(card.model(), keyword)
                || contains(card.licensePlate(), keyword)
                || contains(card.providerName(), keyword);
    }

    private boolean matchesLocation(VehicleCardResponse card, String location) {
        if (location == null || location.isEmpty()) {
            return true;
        }
        return contains(card.providerDepotAddress(), location);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private FuelType parseFuelType(String fuelType) {
        if (fuelType == null || fuelType.isBlank()) {
            return null;
        }
        try {
            return FuelType.valueOf(fuelType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PlanType parsePlanType(String planType) {
        if (planType == null || planType.isBlank()) {
            return null;
        }
        try {
            return PlanType.valueOf(planType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
