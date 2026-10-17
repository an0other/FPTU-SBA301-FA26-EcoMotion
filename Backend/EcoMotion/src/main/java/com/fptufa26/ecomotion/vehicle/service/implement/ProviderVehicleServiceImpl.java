package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleStatsResponse;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderVehicleServiceImpl implements ProviderVehicleService {

    private final UserService userService;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final ProviderScheduleService providerScheduleService;

    @Override
    @Transactional(readOnly = true)
    public ProviderDashboardSummaryResponse getDashboardSummary(Long userId) {
        Provider provider = findProviderByUserId(userId);
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

    @Override
    @Transactional(readOnly = true)
    public ProviderVehiclePageResponse getProviderVehicles(
            Long userId,
            String keyword,
            FuelType fuelType,
            VehicleStatus status,
            String sortBy,
            int page,
            int size
    ) {
        Provider provider = findProviderByUserId(userId);
        Long providerId = provider.getId();

        long total = vehicleRepository.countByProvider_Id(providerId);
        long approved = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.APPROVED);
        long pending = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.PENDING);
        long hidden = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.HIDDEN);
        long rejected = vehicleRepository.countByProvider_IdAndStatus(providerId, VehicleStatus.REJECTED);
        ProviderVehicleStatsResponse stats = new ProviderVehicleStatsResponse(total, approved, pending, hidden, rejected);

        List<Vehicle> allVehicles = vehicleRepository.findByProvider_IdOrderByIdAsc(providerId);
        if (allVehicles.isEmpty()) {
            return new ProviderVehiclePageResponse(stats, List.of(), Math.max(page, 0), Math.max(size, 1), 0, 0);
        }

        List<Long> allVehicleIds = allVehicles.stream().map(Vehicle::getId).toList();
        List<RentalPlan> allActivePlans = rentalPlanRepository.findByVehicle_IdInAndStatusOrderByIdAsc(allVehicleIds, PlanStatus.ACTIVE);
        Map<Long, List<RentalPlan>> plansByVehicle = allActivePlans.stream()
                .collect(Collectors.groupingBy(rp -> rp.getVehicle().getId()));

        List<VehicleImage> allImages = vehicleImageRepository.findByVehicle_IdInOrderByDisplayOrderAsc(allVehicleIds);
        Map<Long, String> coverImageByVehicle = allImages.stream()
                .collect(Collectors.toMap(
                        img -> img.getVehicle().getId(),
                        VehicleImage::getImageUrl,
                        (first, second) -> first
                ));

        String kw = (keyword != null) ? keyword.trim().toLowerCase(Locale.ROOT) : "";

        List<Vehicle> filteredList = allVehicles.stream()
                .filter(v -> {
                    if (!kw.isEmpty()) {
                        boolean matchPlate = v.getLicensePlate() != null && v.getLicensePlate().toLowerCase(Locale.ROOT).contains(kw);
                        boolean matchBrand = v.getBrand() != null && v.getBrand().toLowerCase(Locale.ROOT).contains(kw);
                        boolean matchModel = v.getModel() != null && v.getModel().toLowerCase(Locale.ROOT).contains(kw);
                        if (!matchPlate && !matchBrand && !matchModel) {
                            return false;
                        }
                    }
                    if (fuelType != null && v.getFuelType() != fuelType) {
                        return false;
                    }
                    if (status != null && v.getStatus() != status) {
                        return false;
                    }
                    return true;
                })
                .sorted(resolveComparator(sortBy, plansByVehicle))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? 10 : Math.min(size, 100);
        long totalItems = filteredList.size();
        int totalPages = (int) Math.ceil((double) totalItems / safeSize);

        int fromIndex = Math.min(safePage * safeSize, (int) totalItems);
        int toIndex = Math.min(fromIndex + safeSize, (int) totalItems);
        List<Vehicle> pagedVehicles = filteredList.subList(fromIndex, toIndex);

        List<ProviderVehicleItemResponse> items = pagedVehicles.stream()
                .map(v -> mapToItemResponse(v, plansByVehicle.getOrDefault(v.getId(), List.of()), coverImageByVehicle.get(v.getId())))
                .toList();

        return new ProviderVehiclePageResponse(stats, items, safePage, safeSize, totalItems, totalPages);
    }

    @Override
    @Transactional
    public ProviderVehicleItemResponse toggleVehicleVisibility(
            Long userId,
            Long vehicleId,
            VehicleVisibilityRequest request
    ) {
        if (request == null || request.visible() == null) {
            throw new IllegalArgumentException("visible flag is required");
        }

        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        if (vehicle.getStatus() == VehicleStatus.PENDING) {
            throw new IllegalArgumentException("Xe đang chờ duyệt, chưa thể bật nhận khách");
        }
        if (vehicle.getStatus() == VehicleStatus.REJECTED) {
            throw new IllegalArgumentException("Xe bị từ chối hồ sơ, không thể bật nhận khách");
        }

        VehicleStatus targetStatus = Boolean.TRUE.equals(request.visible()) ? VehicleStatus.APPROVED : VehicleStatus.HIDDEN;
        vehicle.setStatus(targetStatus);
        vehicle = vehicleRepository.save(vehicle);

        List<RentalPlan> plans = rentalPlanRepository.findByVehicle_IdAndStatusOrderByIdAsc(vehicle.getId(), PlanStatus.ACTIVE);
        List<VehicleImage> images = vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(vehicle.getId());
        String coverImage = images.isEmpty() ? null : images.get(0).getImageUrl();

        return mapToItemResponse(vehicle, plans, coverImage);
    }

    private Provider findProviderByUserId(Long userId) {
        return userService.findProviderByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Provider profile not found for user: " + userId));
    }

    private Comparator<Vehicle> resolveComparator(String sortBy, Map<Long, List<RentalPlan>> plansByVehicle) {
        String normalizedSort = (sortBy != null) ? sortBy.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalizedSort) {
            case "oldest", "created-asc" -> Comparator.comparing(Vehicle::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "price-asc" -> Comparator.comparing(v -> getEffectivePrice(v, plansByVehicle), Comparator.nullsLast(BigDecimal::compareTo));
            case "price-desc" -> Comparator.comparing(v -> getEffectivePrice(v, plansByVehicle), Comparator.nullsLast(Comparator.reverseOrder()));
            case "rating", "rating-desc" -> Comparator.comparing(Vehicle::getRatingAvg, Comparator.nullsLast(BigDecimal::compareTo)).reversed();
            case "name-asc", "alphabetical" -> Comparator.comparing(Vehicle::getBrand, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Vehicle::getModel, String.CASE_INSENSITIVE_ORDER);
            case "license_asc", "plate-asc" -> Comparator.comparing(Vehicle::getLicensePlate, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "license_desc", "plate-desc" -> Comparator.comparing(Vehicle::getLicensePlate, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)).reversed();
            case "year-desc" -> Comparator.comparing(Vehicle::getManufactureYear, Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Vehicle::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Vehicle::getId, Comparator.reverseOrder());
        };
    }

    private BigDecimal getEffectivePrice(Vehicle vehicle, Map<Long, List<RentalPlan>> plansByVehicle) {
        List<RentalPlan> plans = plansByVehicle.getOrDefault(vehicle.getId(), List.of());
        return plans.stream()
                .filter(p -> p.getPlanType() == PlanType.DAILY)
                .map(RentalPlan::getSlotPrice)
                .findFirst()
                .orElseGet(() -> plans.stream()
                        .filter(p -> p.getPlanType() == PlanType.HOURLY)
                        .map(RentalPlan::getSlotPrice)
                        .findFirst()
                        .orElse(null));
    }

    private ProviderVehicleItemResponse mapToItemResponse(Vehicle vehicle, List<RentalPlan> activePlans, String coverImageUrl) {
        BigDecimal dailyPrice = activePlans.stream()
                .filter(p -> p.getPlanType() == PlanType.DAILY)
                .map(RentalPlan::getSlotPrice)
                .findFirst()
                .orElse(null);

        BigDecimal hourlyPrice = activePlans.stream()
                .filter(p -> p.getPlanType() == PlanType.HOURLY)
                .map(RentalPlan::getSlotPrice)
                .findFirst()
                .orElse(null);

        return new ProviderVehicleItemResponse(
                vehicle.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getStatus(),
                vehicle.getRatingAvg(),
                vehicle.getRatingCount(),
                coverImageUrl,
                dailyPrice,
                hourlyPrice,
                vehicle.getCreatedAt()
        );
    }
}
