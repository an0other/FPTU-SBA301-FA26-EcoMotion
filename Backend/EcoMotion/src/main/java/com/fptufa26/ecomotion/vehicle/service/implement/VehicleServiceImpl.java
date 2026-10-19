package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleAvailabilityResponse;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.user.dto.ProviderSummary;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.dto.RentalPlanData;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleMapResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.exception.VehicleNotFoundException;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import com.fptufa26.ecomotion.vehicle.service.RentalPlanService;
import com.fptufa26.ecomotion.vehicle.service.VehicleImageService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.vehicle.enums.VehicleSort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final VehicleRepository vehicleRepository;
    private final VehicleImageService vehicleImageService;
    private final RentalPlanService rentalPlanService;
    private final UserService userService;

    @Autowired private com.fptufa26.ecomotion.vehicle.repository.VehicleDocumentRepository vehicleDocumentRepository;
    // Availability dependencies are field-injected to keep the catalog service constructor boundary small.
    @Autowired private VehicleScheduleRepository vehicleScheduleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPolicySnapshotRepository snapshotRepository;
    @Autowired private BookingChargeRepository chargeRepository;

    private static final Set<BookingStatus> BLOCKING_STATUSES = EnumSet.of(
            BookingStatus.PENDING, BookingStatus.ACCEPTED,
            BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED,
            BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    private static final long BUFFER_MINUTES = 60;

    @Override
    public PageResponse<VehicleCardResponse> listRentableVehicles(String keyword, String location,
                                                                  String fuelType, String planType,
                                                                  BigDecimal maxPrice,
                                                                  VehicleSort sort,
                                                                  int offset, int limit) {
        return PageResponse.of(matchingCards(keyword, location, fuelType, planType, maxPrice, sort),
                offset, limit);
    }

    @Override
    public List<VehicleMapResponse> findNearbyVehicles(BigDecimal latitude, BigDecimal longitude,
                                                        int radiusKm, String keyword, String location,
                                                        String fuelType, String planType,
                                                        BigDecimal maxPrice, VehicleSort sort) {
        validateCoordinates(latitude, longitude);
        if (radiusKm != 10 && radiusKm != 15) {
            throw new IllegalArgumentException("radiusKm must be 10 or 15");
        }

        double userLatitude = Math.toRadians(latitude.doubleValue());
        double userLongitude = Math.toRadians(longitude.doubleValue());
        List<Vehicle> vehicles = vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED);
        Map<Long, ProviderSummary> providers = findProviders(vehicles);
        return matchingCards(vehicles, providers, keyword, location, fuelType, planType, maxPrice, sort).stream()
                .map(card -> {
                    ProviderSummary provider = card.providerId() == null ? null : providers.get(card.providerId());
                    if (provider == null || provider.depotLatitude() == null
                            || provider.depotLongitude() == null) {
                        return null;
                    }
                    double distance = distanceKm(userLatitude, userLongitude,
                            provider.depotLatitude().doubleValue(),
                            provider.depotLongitude().doubleValue());
                    return distance <= radiusKm
                            ? new VehicleMapResponse(card, provider.depotLatitude(),
                            provider.depotLongitude(), distance)
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<VehicleCardResponse> matchingCards(String keyword, String location,
                                                     String fuelType, String planType,
                                                     BigDecimal maxPrice, VehicleSort sort) {
        List<Vehicle> vehicles = vehicleRepository.findByStatusOrderByIdAsc(VehicleStatus.APPROVED);
        return matchingCards(vehicles, findProviders(vehicles), keyword, location, fuelType, planType,
                maxPrice, sort);
    }

    private Map<Long, ProviderSummary> findProviders(List<Vehicle> vehicles) {
        List<Long> providerIds = vehicles.stream()
                .map(Vehicle::getProviderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return providerIds.isEmpty() ? Map.of() : userService.findProvidersByIds(providerIds);
    }

    private ProviderSummary providerFor(Vehicle vehicle, Map<Long, ProviderSummary> providers) {
        Long providerId = vehicle.getProviderId();
        return providerId == null ? null : providers.get(providerId);
    }

    private List<VehicleCardResponse> matchingCards(List<Vehicle> vehicles,
                                                     Map<Long, ProviderSummary> providers,
                                                     String keyword, String location,
                                                     String fuelType, String planType,
                                                     BigDecimal maxPrice, VehicleSort sort) {
        if (vehicles.isEmpty()) {
            return List.of();
        }

        List<Long> vehicleIds = vehicles.stream().map(Vehicle::getId).toList();

        Map<Long, List<String>> imagesByVehicle = vehicleImageService.findImageUrlsByVehicleIds(vehicleIds);

        Map<Long, List<RentalPlanData>> plansByVehicle = rentalPlanService.findActivePlansByVehicleIds(vehicleIds);

        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedLocation = location == null ? null : location.trim().toLowerCase(Locale.ROOT);
        FuelType fuelFilter = parseFuelType(fuelType);
        PlanType planFilter = parsePlanType(planType);

        return vehicles.stream()
                .filter(vehicle -> matchesKeyword(vehicle, providerFor(vehicle, providers), normalizedKeyword))
                .filter(vehicle -> matchesLocation(providerFor(vehicle, providers), normalizedLocation))
                .filter(vehicle -> !plansFor(plansByVehicle, vehicle).isEmpty())
                .filter(vehicle -> fuelFilter == null || vehicle.getFuelType() == fuelFilter)
                .filter(vehicle -> planFilter == null || plansFor(plansByVehicle, vehicle).stream()
                        .anyMatch(plan -> plan.planType() == planFilter))
                .filter(vehicle -> maxPrice == null
                        || cheapestSlotPrice(plansFor(plansByVehicle, vehicle), planFilter)
                        .compareTo(maxPrice) <= 0)
                .map(vehicle -> toCard(vehicle,
                        imagesByVehicle.getOrDefault(vehicle.getId(), List.of()),
                        plansFor(plansByVehicle, vehicle), providerFor(vehicle, providers)))
                .sorted(comparatorFor(sort, planFilter))
                .toList();
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Invalid latitude or longitude");
        }
    }

    private double distanceKm(double userLatitude, double userLongitude,
                              double depotLatitude, double depotLongitude) {
        double deltaLatitude = Math.toRadians(depotLatitude) - userLatitude;
        double deltaLongitude = Math.toRadians(depotLongitude) - userLongitude;
        double haversine = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(userLatitude) * Math.cos(Math.toRadians(depotLatitude))
                * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(haversine)));
    }

    @Override
    public VehicleDetailResponse getVehicleDetail(Long id) {
        Vehicle vehicle = vehicleRepository.findByIdAndStatus(id, VehicleStatus.APPROVED)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        List<String> imageUrls = vehicleImageService.findImageUrls(vehicle.getId());

        List<VehicleDetailResponse.PlanDetail> plans = rentalPlanService.findActivePlans(vehicle.getId()).stream()
                .map(this::toPlanDetail)
                .toList();

        return new VehicleDetailResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getRatingAvg(),
                vehicle.getRatingCount(),
                imageUrls,
                toProviderInfo(providerFor(vehicle, findProviders(List.of(vehicle)))),
                plans);
    }

    private List<RentalPlanData> plansFor(Map<Long, List<RentalPlanData>> plansByVehicle, Vehicle vehicle) {
        return plansByVehicle.getOrDefault(vehicle.getId(), List.of());
    }

    private VehicleCardResponse toCard(Vehicle vehicle, List<String> imageUrls,
                                       List<RentalPlanData> plans, ProviderSummary provider) {
        List<VehicleCardResponse.PlanPrice> planPrices = plans.stream()
                .map(plan -> new VehicleCardResponse.PlanPrice(
                        plan.planType(),
                        plan.slotLengthMinutes(),
                        plan.slotPrice(),
                        plan.upfrontAmount()))
                .toList();

        return new VehicleCardResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getRatingAvg(),
                vehicle.getRatingCount(),
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                imageUrls,
                provider == null ? null : provider.id(),
                provider == null ? null : provider.displayName(),
                provider == null ? null : provider.hotline(),
                provider == null ? null : provider.depotAddress(),
                planPrices);
    }

    private VehicleDetailResponse.ProviderInfo toProviderInfo(ProviderSummary provider) {
        if (provider == null) {
            return null;
        }
        return new VehicleDetailResponse.ProviderInfo(
                provider.id(),
                provider.displayName(),
                provider.providerType(),
                provider.hotline(),
                provider.depotAddress(),
                provider.ratingAvg(),
                provider.ratingCount());
    }

    private VehicleDetailResponse.PlanDetail toPlanDetail(RentalPlanData plan) {
        return new VehicleDetailResponse.PlanDetail(
                plan.id(),
                plan.planType(),
                plan.slotLengthMinutes(),
                plan.slotPrice(),
                plan.upfrontAmount(),
                plan.allowedOvertimeMinutes(),
                plan.overtimeSurchargeRatio(),
                plan.freeRadiusKm(),
                plan.outOfRadiusRate(),
                plan.waitingFreeMinutes(),
                plan.waitingRate(),
                plan.weekendSurchargeRatio(),
                plan.hourlyToDailyThresholdMinutes());
    }

    private BigDecimal cheapestSlotPrice(List<RentalPlanData> plans, PlanType planFilter) {
        return plans.stream()
                .filter(plan -> planFilter == null || plan.planType() == planFilter)
                .map(RentalPlanData::slotPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.valueOf(Long.MAX_VALUE));
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

    private boolean matchesKeyword(Vehicle vehicle, ProviderSummary provider, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        return contains(vehicle.getBrand(), keyword)
                || contains(vehicle.getModel(), keyword)
                || contains(vehicle.getLicensePlate(), keyword)
                || contains(provider == null ? null : provider.displayName(), keyword);
    }

    private boolean matchesLocation(ProviderSummary provider, String location) {
        if (location == null || location.isEmpty()) {
            return true;
        }
        return contains(provider == null ? null : provider.depotAddress(), location);
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

    @Override
    public VehicleAvailabilityResponse getAvailability(Long vehicleId, Instant from, Instant to) {
        // Verify vehicle exists and is approved
        vehicleRepository.findByIdAndStatus(vehicleId, VehicleStatus.APPROVED)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        List<VehicleAvailabilityResponse.OccupiedSlot> result = new ArrayList<>();

        // Expand query range by buffer so edge bookings are not missed
        Duration buffer = Duration.ofMinutes(BUFFER_MINUTES);
        Instant expandedFrom = from.minus(buffer);
        Instant expandedTo = to.plus(buffer);

        // 1. Schedule-based slots (CONFIRMED bookings, maintenance, blocked)
        var schedules = vehicleScheduleRepository.findActiveInRange(vehicleId, expandedFrom, expandedTo);
        for (var s : schedules) {
            result.add(new VehicleAvailabilityResponse.OccupiedSlot(
                    s.getStartAt().minus(buffer), s.getEndAt().plus(buffer), s.getType().name()));
        }

        // 2. Booking-based slots (PENDING/ACCEPTED that don't yet have a schedule row)
        var blockingBookings = bookingRepository.findByVehicle_IdAndStatusIn(vehicleId, BLOCKING_STATUSES);
        for (Booking b : blockingBookings) {
            Instant bookingEnd = computeBookingEnd(b);
            // Only include if it overlaps the requested range and isn't already covered by a schedule
            if (bookingEnd != null && b.getPickupTime().isBefore(expandedTo) && bookingEnd.isAfter(expandedFrom)) {
                boolean alreadyCovered = schedules.stream().anyMatch(s ->
                        s.getBooking() != null && s.getBooking().getId().equals(b.getId()));
                if (!alreadyCovered) {
                    result.add(new VehicleAvailabilityResponse.OccupiedSlot(
                            b.getPickupTime().minus(buffer), bookingEnd.plus(buffer), "BOOKED"));
                }
            }
        }

        result.sort(Comparator.comparing(VehicleAvailabilityResponse.OccupiedSlot::startAt));
        return new VehicleAvailabilityResponse(vehicleId, result);
    }

    private Instant computeBookingEnd(Booking booking) {
        BookingPolicySnapshot snapshot = snapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        if (snapshot == null) return null;
        int slots = chargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                .map(BookingCharge::getQuantity)
                .orElse(1);
        return booking.getPickupTime().plus(Duration.ofMinutes((long) snapshot.getSlotLengthMinutes() * slots));
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

    @Override
    public String getCoverImageUrl(Long vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        List<String> images = vehicleImageService.findImageUrls(vehicleId);
        return images.isEmpty() ? null : images.get(0);
    }

    @Override
    public java.util.Map<Long, String> getCoverImageUrls(java.util.Collection<Long> vehicleIds) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<Long, List<String>> images = vehicleImageService.findImageUrlsByVehicleIds(vehicleIds);
        Map<Long, String> result = new java.util.HashMap<>();
        images.forEach((id, urls) -> {
            if (!urls.isEmpty()) {
                result.put(id, urls.get(0));
            }
        });
        return result;
    }

    @Override
    @Transactional
    public void updateRating(
            Long vehicleId,
            BigDecimal ratingAvg,
            int ratingCount) {
        if (vehicleId == null) {
            throw new IllegalArgumentException("Vehicle id is required");
        }

        if (ratingAvg == null
                || ratingAvg.signum() < 0
                || ratingAvg.compareTo(BigDecimal.valueOf(5)) > 0
                || ratingCount < 0
                || (ratingCount == 0 && ratingAvg.signum() != 0)
                || (ratingCount > 0
                && ratingAvg.compareTo(BigDecimal.ONE) < 0)) {
            throw new IllegalArgumentException("Invalid vehicle rating summary");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        vehicle.setRatingAvg(ratingAvg);
        vehicle.setRatingCount(ratingCount);
    }

    // ---------------------------------------------------------
    // Admin Vehicle Methods (TV6 - A03)
    // ---------------------------------------------------------

    @Override
    public List<Vehicle> listAdminVehicles(VehicleStatus status, String keyword) {
        List<Vehicle> vehicles = (status != null)
                ? vehicleRepository.findByStatusOrderByIdDesc(status)
                : vehicleRepository.findAllByOrderByIdDesc();

        if (keyword != null && !keyword.isBlank()) {
            String lower = keyword.trim().toLowerCase(Locale.ROOT);
            return vehicles.stream()
                    .filter(v -> (v.getLicensePlate() != null && v.getLicensePlate().toLowerCase(Locale.ROOT).contains(lower))
                            || (v.getModel() != null && v.getModel().toLowerCase(Locale.ROOT).contains(lower))
                            || (v.getBrand() != null && v.getBrand().toLowerCase(Locale.ROOT).contains(lower)))
                    .toList();
        }
        return vehicles;
    }

    @Override
    public Vehicle getAdminVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    @Override
    public List<com.fptufa26.ecomotion.vehicle.entity.VehicleDocument> getVehicleDocuments(Long vehicleId) {
        return vehicleDocumentRepository.findByVehicle_Id(vehicleId);
    }

    @Override
    @Transactional
    public Vehicle updateVehicleApproval(Long vehicleId, VehicleStatus status, String reason) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));

        vehicle.setStatus(status != null ? status : VehicleStatus.APPROVED);

        if (status == VehicleStatus.APPROVED) {
            List<com.fptufa26.ecomotion.vehicle.entity.VehicleDocument> docs = vehicleDocumentRepository.findByVehicle_Id(vehicleId);
            for (com.fptufa26.ecomotion.vehicle.entity.VehicleDocument doc : docs) {
                if (doc.getStatus() == com.fptufa26.ecomotion.vehicle.enums.DocumentStatus.PENDING) {
                    doc.setStatus(com.fptufa26.ecomotion.vehicle.enums.DocumentStatus.VERIFIED);
                    vehicleDocumentRepository.save(doc);
                }
            }
        } else if (status == VehicleStatus.REJECTED) {
            List<com.fptufa26.ecomotion.vehicle.entity.VehicleDocument> docs = vehicleDocumentRepository.findByVehicle_Id(vehicleId);
            for (com.fptufa26.ecomotion.vehicle.entity.VehicleDocument doc : docs) {
                if (doc.getStatus() == com.fptufa26.ecomotion.vehicle.enums.DocumentStatus.PENDING) {
                    doc.setStatus(com.fptufa26.ecomotion.vehicle.enums.DocumentStatus.REJECTED);
                    vehicleDocumentRepository.save(doc);
                }
            }
        }

        return vehicleRepository.save(vehicle);
    }

    @Override
    public long countVehiclesByStatus(VehicleStatus status) {
        return vehicleRepository.countByStatus(status);
    }

    @Override
    public long countTotalVehicles() {
        return vehicleRepository.count();
    }
}
