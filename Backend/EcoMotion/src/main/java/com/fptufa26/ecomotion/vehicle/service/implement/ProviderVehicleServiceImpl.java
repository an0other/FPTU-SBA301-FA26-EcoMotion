package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateRentalPlansRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDocumentRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDepositPolicyRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderRentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDocumentResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleStatsResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDepositPolicyResponse;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.DocumentStatus;
import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanStatus;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.RentalPlanRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleDocumentRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.ProviderVehicleService;
import com.fptufa26.ecomotion.vehicle.service.LicensePlatePolicy;
import com.fptufa26.ecomotion.vehicle.service.LicensePlateHistoryService;
import com.fptufa26.ecomotion.vehicle.service.VehicleDepositPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProviderVehicleServiceImpl implements ProviderVehicleService {

    private static final BigDecimal DEPOSIT_EXAMPLE_TOTAL = new BigDecimal("10000000.00");

    private final UserService userService;
    private final VehicleRepository vehicleRepository;
    private final RentalPlanRepository rentalPlanRepository;
    private final VehicleImageRepository vehicleImageRepository;
    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final ProviderScheduleService providerScheduleService;
    private final VehicleDepositPolicyService vehicleDepositPolicyService;
    private final LicensePlateHistoryService licensePlateHistoryService;

    public ProviderVehicleServiceImpl(
            UserService userService,
            VehicleRepository vehicleRepository,
            RentalPlanRepository rentalPlanRepository,
            VehicleImageRepository vehicleImageRepository,
            VehicleDocumentRepository vehicleDocumentRepository,
            ProviderScheduleService providerScheduleService,
            VehicleDepositPolicyService vehicleDepositPolicyService,
            LicensePlateHistoryService licensePlateHistoryService
    ) {
        this.userService = userService;
        this.vehicleRepository = vehicleRepository;
        this.rentalPlanRepository = rentalPlanRepository;
        this.vehicleImageRepository = vehicleImageRepository;
        this.vehicleDocumentRepository = vehicleDocumentRepository;
        this.providerScheduleService = providerScheduleService;
        this.vehicleDepositPolicyService = vehicleDepositPolicyService;
        this.licensePlateHistoryService = licensePlateHistoryService;
    }

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
        long currentlyBlocked = providerScheduleService.countCurrentlyBlockedVehiclesByProviderId(providerId);
        long availableVehicles = Math.max(0, approvedVehicles - currentlyBlocked);

        return new ProviderDashboardSummaryResponse(
                provider.getDisplayName(),
                provider.getDepotAddress(),
                provider.getHotline(),
                totalVehicles,
                pendingVehicles,
                approvedVehicles,
                hiddenVehicles,
                totalDrivers,
                activeBlocks,
                availableVehicles
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

    @Override
    @Transactional
    public ProviderVehicleDetailResponse createVehicle(Long userId, CreateVehicleRequest request) {
        Provider provider = findProviderByUserId(userId);

        String licensePlate = LicensePlatePolicy.display(request.licensePlate());
        String chassisNo = request.chassisNo().trim().toUpperCase(Locale.ROOT);
        String engineNo = request.engineNo().trim().toUpperCase(Locale.ROOT);

        if (!chassisNo.matches("^[A-Z0-9-]{6,50}$")) {
            throw new IllegalArgumentException("Số khung (VIN) không hợp lệ (tối thiểu 6 ký tự chữ, số hoặc gạch nối)");
        }
        if (!engineNo.matches("^[A-Z0-9-]{4,50}$")) {
            throw new IllegalArgumentException("Số máy không hợp lệ (tối thiểu 4 ký tự chữ, số hoặc gạch nối)");
        }

        if (plateExists(licensePlate, null)) {
            throw new IllegalArgumentException("Biển số xe " + licensePlate + " đã tồn tại trên hệ thống");
        }
        if (vehicleRepository.existsByChassisNoIgnoreCase(chassisNo)) {
            throw new IllegalArgumentException("Số khung (VIN) " + chassisNo + " đã tồn tại trên hệ thống");
        }
        if (vehicleRepository.existsByEngineNoIgnoreCase(engineNo)) {
            throw new IllegalArgumentException("Số máy " + engineNo + " đã tồn tại trên hệ thống");
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setProvider(provider);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setChassisNo(chassisNo);
        vehicle.setEngineNo(engineNo);
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setSeatCount(request.seatCount());
        vehicle.setTransmission(request.transmission());
        vehicle.setFuelType(request.fuelType());
        vehicle.setColor(request.color().trim());
        vehicle.setStatus(VehicleStatus.PENDING);
        vehicle.setMaintenanceBufferMinutes(request.maintenanceBufferMinutes());
        vehicle.setRatingAvg(BigDecimal.ZERO);
        vehicle.setRatingCount(0);

        Vehicle saved = vehicleRepository.save(vehicle);

        String coverImageUrl = null;
        if (request.coverImageUrl() != null && !request.coverImageUrl().isBlank()) {
            VehicleImage image = new VehicleImage();
            image.setVehicle(saved);
            image.setImageUrl(request.coverImageUrl().trim());
            image.setDisplayOrder(0);
            image.setCaption("Ảnh bìa chính thức");
            vehicleImageRepository.save(image);
            coverImageUrl = image.getImageUrl();
        }

        return new ProviderVehicleDetailResponse(
                saved.getId(),
                saved.getLicensePlate(),
                saved.getChassisNo(),
                saved.getEngineNo(),
                saved.getBrand(),
                saved.getModel(),
                saved.getManufactureYear(),
                saved.getSeatCount(),
                saved.getTransmission(),
                saved.getFuelType(),
                saved.getColor(),
                saved.getStatus(),
                saved.getMaintenanceBufferMinutes(),
                coverImageUrl,
                saved.getRatingAvg(),
                saved.getRatingCount(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderVehicleDetailResponse getVehicleDetail(Long userId, Long vehicleId) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        String coverImageUrl = vehicleImageRepository.findByVehicle_IdInOrderByDisplayOrderAsc(List.of(vehicleId))
                .stream()
                .filter(img -> img.getDisplayOrder() == 0)
                .map(VehicleImage::getImageUrl)
                .findFirst()
                .orElse(null);

        List<ProviderRentalPlanResponse> plans = buildRentalPlanResponses(vehicle);
        List<ProviderVehicleDocumentResponse> documents = vehicleDocumentRepository.findByVehicle_Id(vehicleId)
                .stream()
                .map(this::mapToDocumentResponse)
                .toList();

        return new ProviderVehicleDetailResponse(
                vehicle.getId(),
                vehicle.getLicensePlate(),
                vehicle.getChassisNo(),
                vehicle.getEngineNo(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getStatus(),
                vehicle.getMaintenanceBufferMinutes(),
                coverImageUrl,
                vehicle.getRatingAvg(),
                vehicle.getRatingCount(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt(),
                plans,
                documents
        );
    }

    @Override
    @Transactional
    public ProviderVehicleDetailResponse updateVehicle(Long userId, Long vehicleId, UpdateVehicleRequest request) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        String newPlate = LicensePlatePolicy.display(request.licensePlate());

        if (!newPlate.equalsIgnoreCase(vehicle.getLicensePlate())) {
            if (plateExists(newPlate, vehicleId)) {
                throw new IllegalArgumentException("Biển số xe " + newPlate + " đã tồn tại trên hệ thống");
            }
            licensePlateHistoryService.recordChange(
                    vehicle, vehicle.getLicensePlate(), newPlate, provider.getUser());
            vehicle.setLicensePlate(newPlate);
            vehicle.setStatus(VehicleStatus.PENDING);
        }

        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setSeatCount(request.seatCount());
        vehicle.setTransmission(request.transmission());
        vehicle.setFuelType(request.fuelType());
        vehicle.setColor(request.color().trim());
        vehicle.setMaintenanceBufferMinutes(request.maintenanceBufferMinutes());

        Vehicle saved = vehicleRepository.save(vehicle);

        String coverImageUrl = null;
        if (request.coverImageUrl() != null && !request.coverImageUrl().isBlank()) {
            List<VehicleImage> existingImages = vehicleImageRepository.findByVehicle_IdInOrderByDisplayOrderAsc(List.of(vehicleId));
            VehicleImage coverImg = existingImages.stream()
                    .filter(img -> img.getDisplayOrder() == 0)
                    .findFirst()
                    .orElseGet(() -> {
                        VehicleImage newImg = new VehicleImage();
                        newImg.setVehicle(saved);
                        newImg.setDisplayOrder(0);
                        return newImg;
                    });
            coverImg.setImageUrl(request.coverImageUrl().trim());
            coverImg.setCaption("Ảnh bìa chính thức");
            vehicleImageRepository.save(coverImg);
            coverImageUrl = coverImg.getImageUrl();
        } else {
            List<VehicleImage> existingImages = vehicleImageRepository.findByVehicle_IdInOrderByDisplayOrderAsc(List.of(vehicleId));
            coverImageUrl = existingImages.stream()
                    .filter(img -> img.getDisplayOrder() == 0)
                    .map(VehicleImage::getImageUrl)
                    .findFirst()
                    .orElse(null);
        }

        List<ProviderRentalPlanResponse> plans = buildRentalPlanResponses(saved);

        return new ProviderVehicleDetailResponse(
                saved.getId(),
                saved.getLicensePlate(),
                saved.getChassisNo(),
                saved.getEngineNo(),
                saved.getBrand(),
                saved.getModel(),
                saved.getManufactureYear(),
                saved.getSeatCount(),
                saved.getTransmission(),
                saved.getFuelType(),
                saved.getColor(),
                saved.getStatus(),
                saved.getMaintenanceBufferMinutes(),
                coverImageUrl,
                saved.getRatingAvg(),
                saved.getRatingCount(),
                saved.getCreatedAt(),
                saved.getUpdatedAt(),
                plans
        );
    }

    @Override
    @Transactional
    public VehicleDepositPolicyResponse updateDepositPolicy(
            Long userId,
            Long vehicleId,
            UpdateVehicleDepositPolicyRequest request
    ) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        vehicle.setDepositRatioOverride(request.depositRatioOverride());
        vehicle.setMinimumDepositAmount(request.minimumDepositAmount() == null
                ? BigDecimal.ZERO
                : request.minimumDepositAmount());
        vehicleRepository.save(vehicle);
        return depositPolicyResponse(vehicle, provider);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleDepositPolicyResponse getDepositPolicy(Long userId, Long vehicleId) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        return depositPolicyResponse(vehicle, provider);
    }

    private VehicleDepositPolicyResponse depositPolicyResponse(Vehicle vehicle, Provider provider) {
        var quote = vehicleDepositPolicyService.resolve(vehicle, DEPOSIT_EXAMPLE_TOTAL);
        BigDecimal providerRatio = provider.getDepositRatio() == null
                ? new BigDecimal("0.3000")
                : provider.getDepositRatio();
        return new VehicleDepositPolicyResponse(
                vehicle.getId(),
                providerRatio,
                vehicle.getDepositRatioOverride(),
                quote.minimumAmount(),
                quote.ratio(),
                DEPOSIT_EXAMPLE_TOTAL,
                quote.amount()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderRentalPlanResponse> getVehicleRentalPlans(Long userId, Long vehicleId) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        return buildRentalPlanResponses(vehicle);
    }

    @Override
    @Transactional
    public List<ProviderRentalPlanResponse> updateVehicleRentalPlans(
            Long userId,
            Long vehicleId,
            UpdateRentalPlansRequest request
    ) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        for (UpdateRentalPlansRequest.PlanItemRequest item : request.plans()) {
            if (item.status() == PlanStatus.ACTIVE) {
                if (item.slotPrice() == null || item.slotPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Vui lòng nhập đơn giá lớn hơn 0 cho gói cước đang kích hoạt: " + item.planType());
                }
            }

            RentalPlan plan = rentalPlanRepository.findByVehicle_IdAndPlanType(vehicle.getId(), item.planType())
                    .orElse(null);

            if (plan != null
                    && item.slotPrice() != null
                    && isUnusualPriceChange(plan.getSlotPrice(), item.slotPrice())
                    && !Boolean.TRUE.equals(request.confirmUnusualPriceChange())) {
                throw new IllegalArgumentException(
                        "Mức giá thay đổi bất thường từ 50% trở lên; vui lòng xác nhận trước khi lưu"
                );
            }

            if (plan == null && item.status() == PlanStatus.INACTIVE && (item.slotPrice() == null || item.slotPrice().compareTo(BigDecimal.ZERO) <= 0)) {
                continue;
            }

            if (plan == null) {
                plan = new RentalPlan();
                plan.setVehicle(vehicle);
                plan.setPlanType(item.planType());
                plan.setSlotLengthMinutes(defaultSlotLength(item.planType()));
            }

            if (item.slotPrice() != null) {
                plan.setSlotPrice(item.slotPrice());
            } else if (plan.getSlotPrice() == null) {
                plan.setSlotPrice(BigDecimal.ZERO);
            }
            plan.setStatus(item.status());

            if (item.planType() == PlanType.YEARLY) {
                plan.setUpfrontAmount(item.upfrontAmount() != null ? item.upfrontAmount() : BigDecimal.ZERO);
                plan.setHourlyToDailyThresholdMinutes(null);
                plan.setAllowedOvertimeMinutes(null);
                plan.setOvertimeSurchargeRatio(null);
                plan.setFreeRadiusKm(null);
                plan.setOutOfRadiusRate(null);
                plan.setWaitingFreeMinutes(null);
                plan.setWaitingRate(null);
                plan.setWeekendSurchargeRatio(null);
            } else {
                plan.setUpfrontAmount(null);
                if (item.planType() == PlanType.HOURLY) {
                    plan.setHourlyToDailyThresholdMinutes(
                            item.hourlyToDailyThresholdMinutes() != null ? item.hourlyToDailyThresholdMinutes() : 480
                    );
                } else {
                    plan.setHourlyToDailyThresholdMinutes(null);
                }
                plan.setAllowedOvertimeMinutes(request.allowedOvertimeMinutes());
                plan.setOvertimeSurchargeRatio(request.overtimeSurchargeRatio());
                plan.setFreeRadiusKm(request.freeRadiusKm());
                plan.setOutOfRadiusRate(request.outOfRadiusRate());
                plan.setWaitingFreeMinutes(request.waitingFreeMinutes());
                plan.setWaitingRate(request.waitingRate());
                plan.setWeekendSurchargeRatio(request.weekendSurchargeRatio());
            }

            rentalPlanRepository.save(plan);
        }

        return buildRentalPlanResponses(vehicle);
    }

    private List<ProviderRentalPlanResponse> buildRentalPlanResponses(Vehicle vehicle) {
        List<RentalPlan> existing = rentalPlanRepository.findByVehicle_IdOrderByIdAsc(vehicle.getId());
        Map<PlanType, RentalPlan> planMap = existing.stream()
                .collect(Collectors.toMap(RentalPlan::getPlanType, p -> p, (a, b) -> a));

        RentalPlan refPlan = existing.stream()
                .filter(p -> p.getPlanType() != PlanType.YEARLY && p.getAllowedOvertimeMinutes() != null)
                .findFirst()
                .orElse(null);

        Integer defaultAllowedOvertime = refPlan != null ? refPlan.getAllowedOvertimeMinutes() : 15;
        BigDecimal defaultOvertimeRatio = refPlan != null ? refPlan.getOvertimeSurchargeRatio() : new BigDecimal("0.1500");
        Integer defaultFreeRadius = refPlan != null ? refPlan.getFreeRadiusKm() : 10;
        BigDecimal defaultOutOfRadiusRate = refPlan != null ? refPlan.getOutOfRadiusRate() : new BigDecimal("12000.00");
        Integer defaultWaitingFree = refPlan != null ? refPlan.getWaitingFreeMinutes() : 15;
        BigDecimal defaultWaitingRate = refPlan != null ? refPlan.getWaitingRate() : new BigDecimal("2000.00");
        BigDecimal defaultWeekendRatio = refPlan != null ? refPlan.getWeekendSurchargeRatio() : new BigDecimal("0.1500");

        List<ProviderRentalPlanResponse> result = new ArrayList<>();
        for (PlanType type : List.of(PlanType.HOURLY, PlanType.DAILY, PlanType.MONTHLY, PlanType.YEARLY)) {
            RentalPlan plan = planMap.get(type);
            if (plan != null) {
                result.add(toRentalPlanResponse(plan));
            } else {
                result.add(createDefaultPlanResponse(
                        type,
                        defaultAllowedOvertime,
                        defaultOvertimeRatio,
                        defaultFreeRadius,
                        defaultOutOfRadiusRate,
                        defaultWaitingFree,
                        defaultWaitingRate,
                        defaultWeekendRatio
                ));
            }
        }
        return result;
    }

    private ProviderRentalPlanResponse createDefaultPlanResponse(
            PlanType type,
            Integer allowedOvertimeMinutes,
            BigDecimal overtimeSurchargeRatio,
            Integer freeRadiusKm,
            BigDecimal outOfRadiusRate,
            Integer waitingFreeMinutes,
            BigDecimal waitingRate,
            BigDecimal weekendSurchargeRatio
    ) {
        return switch (type) {
            case HOURLY -> new ProviderRentalPlanResponse(
                    null,
                    PlanType.HOURLY,
                    60,
                    null,
                    null,
                    PlanStatus.INACTIVE,
                    allowedOvertimeMinutes,
                    overtimeSurchargeRatio,
                    freeRadiusKm,
                    outOfRadiusRate,
                    waitingFreeMinutes,
                    waitingRate,
                    weekendSurchargeRatio,
                    480
            );
            case DAILY -> new ProviderRentalPlanResponse(
                    null,
                    PlanType.DAILY,
                    1440,
                    null,
                    null,
                    PlanStatus.INACTIVE,
                    allowedOvertimeMinutes,
                    overtimeSurchargeRatio,
                    freeRadiusKm,
                    outOfRadiusRate,
                    waitingFreeMinutes,
                    waitingRate,
                    weekendSurchargeRatio,
                    null
            );
            case MONTHLY -> new ProviderRentalPlanResponse(
                    null,
                    PlanType.MONTHLY,
                    43200,
                    null,
                    null,
                    PlanStatus.INACTIVE,
                    allowedOvertimeMinutes,
                    overtimeSurchargeRatio,
                    freeRadiusKm,
                    outOfRadiusRate,
                    waitingFreeMinutes,
                    waitingRate,
                    weekendSurchargeRatio,
                    null
            );
            case YEARLY -> new ProviderRentalPlanResponse(
                    null,
                    PlanType.YEARLY,
                    525600,
                    null,
                    BigDecimal.ZERO,
                    PlanStatus.INACTIVE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        };
    }

    private ProviderRentalPlanResponse toRentalPlanResponse(RentalPlan plan) {
        return new ProviderRentalPlanResponse(
                plan.getId(),
                plan.getPlanType(),
                plan.getSlotLengthMinutes(),
                plan.getSlotPrice(),
                plan.getUpfrontAmount(),
                plan.getStatus(),
                plan.getAllowedOvertimeMinutes(),
                plan.getOvertimeSurchargeRatio(),
                plan.getFreeRadiusKm(),
                plan.getOutOfRadiusRate(),
                plan.getWaitingFreeMinutes(),
                plan.getWaitingRate(),
                plan.getWeekendSurchargeRatio(),
                plan.getHourlyToDailyThresholdMinutes()
        );
    }

    private int defaultSlotLength(PlanType type) {
        return switch (type) {
            case HOURLY -> 60;
            case DAILY -> 1440;
            case MONTHLY -> 43200;
            case YEARLY -> 525600;
        };
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

        BigDecimal monthlyPrice = activePlans.stream()
                .filter(p -> p.getPlanType() == PlanType.MONTHLY)
                .map(RentalPlan::getSlotPrice)
                .findFirst()
                .orElse(null);

        BigDecimal yearlyPrice = activePlans.stream()
                .filter(p -> p.getPlanType() == PlanType.YEARLY)
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
                monthlyPrice,
                yearlyPrice,
                vehicle.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderVehicleDocumentResponse> getVehicleDocuments(Long userId, Long vehicleId) {
        Provider provider = findProviderByUserId(userId);
        vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        return vehicleDocumentRepository.findByVehicle_Id(vehicleId).stream()
                .map(this::mapToDocumentResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProviderVehicleDocumentResponse updateVehicleDocument(Long userId, Long vehicleId, UpdateVehicleDocumentRequest request) {
        Provider provider = findProviderByUserId(userId);
        Vehicle vehicle = vehicleRepository.findByIdAndProvider_Id(vehicleId, provider.getId())
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        if (request.docType() != DocumentType.REGISTRATION && request.expiryDate() == null) {
            throw new IllegalArgumentException("Ngày hết hạn là bắt buộc đối với loại giấy tờ " + request.docType());
        }

        var existingOpt = vehicleDocumentRepository.findByVehicle_IdAndDocType(vehicleId, request.docType());
        VehicleDocument doc;
        boolean replacingVerifiedDocument = existingOpt
                .map(existing -> existing.getStatus() == DocumentStatus.VERIFIED)
                .orElse(false);
        if (existingOpt.isPresent()) {
            doc = existingOpt.get();
            doc.setDocNumber(request.docNumber().trim());
            doc.setExpiryDate(request.docType() == DocumentType.REGISTRATION ? null : request.expiryDate());
            doc.setFileUrl(request.fileUrl().trim());
            doc.setStatus(DocumentStatus.PENDING);
        } else {
            doc = new VehicleDocument();
            doc.setVehicle(vehicle);
            doc.setDocType(request.docType());
            doc.setDocNumber(request.docNumber().trim());
            doc.setExpiryDate(request.docType() == DocumentType.REGISTRATION ? null : request.expiryDate());
            doc.setFileUrl(request.fileUrl().trim());
            doc.setStatus(DocumentStatus.PENDING);
        }

        VehicleDocument saved = vehicleDocumentRepository.save(doc);
        if (replacingVerifiedDocument) {
            vehicle.setStatus(VehicleStatus.PENDING);
            vehicleRepository.save(vehicle);
        }
        return mapToDocumentResponse(saved);
    }

    private ProviderVehicleDocumentResponse mapToDocumentResponse(VehicleDocument doc) {
        return new ProviderVehicleDocumentResponse(
                doc.getId(),
                doc.getVehicle().getId(),
                doc.getDocType(),
                doc.getDocNumber(),
                doc.getExpiryDate(),
                doc.getFileUrl(),
                doc.getStatus(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    private boolean plateExists(String plate, Long excludedVehicleId) {
        String canonical = LicensePlatePolicy.canonical(plate);
        return vehicleRepository.findAll().stream()
                .filter(vehicle -> excludedVehicleId == null || !vehicle.getId().equals(excludedVehicleId))
                .anyMatch(vehicle -> LicensePlatePolicy.canonical(vehicle.getLicensePlate()).equals(canonical));
    }

    private boolean isUnusualPriceChange(BigDecimal currentPrice, BigDecimal nextPrice) {
        if (currentPrice == null || currentPrice.signum() <= 0 || nextPrice == null) {
            return false;
        }
        BigDecimal difference = nextPrice.subtract(currentPrice).abs();
        return difference.compareTo(currentPrice.multiply(new BigDecimal("0.50"))) >= 0;
    }
}
