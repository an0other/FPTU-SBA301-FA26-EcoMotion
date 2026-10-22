package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.LocationUpdateRequest;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverTripResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripLocationResponse;
import com.fptufa26.ecomotion.booking.dto.response.TripTrackingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.VehicleLocation;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripView;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.exception.TripConflictException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.VehicleLocationRepository;
import com.fptufa26.ecomotion.booking.service.DriverTripService;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import com.fptufa26.ecomotion.booking.specification.BookingSpecification;
import com.fptufa26.ecomotion.common.config.ClockConfig;
import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverTripServiceImpl implements DriverTripService {

    private static final Logger log = LoggerFactory.getLogger(DriverTripServiceImpl.class);
    private static final Duration STALE_LOCATION_THRESHOLD = Duration.ofSeconds(60);
    private static final Duration MAX_FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "pickuptime",
            "createdat",
            "totalamount",
            "status",
            "id"
    );

    private final BookingRepository bookingRepository;
    private final VehicleLocationRepository vehicleLocationRepository;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final MapGeocodingService mapGeocodingService;
    private final Clock clock;

    @Override
    public PageResponse<DriverTripResponse> listDriverTrips(
            Long currentUserId,
            String view,
            String status,
            String date,
            int page,
            int size,
            String sort) {

        Driver driver = resolveDriver(currentUserId);

        validatePagination(page, size);

        TripView parsedView = parseView(view);
        Collection<BookingStatus> parsedStatuses = parseStatus(status);
        LocalDate parsedDate = parseDate(date);

        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.VIETNAM_ZONE));
        Instant startOfToday = today.atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant();

        Instant fromInclusive = null;
        Instant toExclusive = null;
        if (parsedDate != null) {
            fromInclusive = parsedDate.atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant();
            toExclusive = parsedDate.plusDays(1).atStartOfDay(ClockConfig.VIETNAM_ZONE).toInstant();
        }

        Sort effectiveSort = resolveSort(sort, parsedView);
        Pageable pageable = PageRequest.of(page, size, effectiveSort);

        Specification<Booking> spec = BookingSpecification.forDriverSchedule(
                driver.getId(),
                parsedView,
                parsedStatuses,
                fromInclusive,
                toExclusive,
                startOfToday,
                startOfTomorrow
        );

        Page<Booking> pageResult = bookingRepository.findAll(spec, pageable);

        Map<Long, String> coverUrls = getCoverUrlsForBookings(pageResult.getContent());
        List<DriverTripResponse> items = pageResult.getContent().stream()
                .map(b -> toResponse(b, coverUrls.get(b.getVehicle() != null ? b.getVehicle().getId() : null)))
                .toList();

        int offset = page * size;
        return new PageResponse<>(items, pageResult.getTotalElements(), offset, size, pageResult.hasNext());
    }

    @Override
    public DriverTripDetailResponse getTripDetail(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        String coverUrl = null;
        if (booking.getVehicle() != null && booking.getVehicle().getId() != null) {
            coverUrl = vehicleService.getCoverImageUrl(booking.getVehicle().getId());
        }

        TripLocationResponse latestLocation = getLatestLocationForBooking(booking.getId());
        return toDetailResponse(booking, coverUrl, latestLocation);
    }

    @Override
    @Transactional
    public DriverTripDetailResponse confirmTrip(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        log.info(
                "Driver {} confirms trip {}, booking {}, oldTripStatus={}, oldBookingStatus={}",
                driver.getId(),
                booking.getId(),
                booking.getBookingCode(),
                deriveExecutionStatus(booking),
                booking.getStatus()
        );

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể xác nhận chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.REJECTED) {
            throw new InvalidTripTransitionException("Không thể xác nhận chuyến đi đã bị từ chối");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new InvalidTripTransitionException("Không thể xác nhận chuyến đi đã hoàn thành");
        }
        if (booking.getDriverConfirmedAt() != null || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
            throw new TripConflictException("Chuyến đi đã được xác nhận trước đó");
        }
        if (booking.getStatus() != BookingStatus.DRIVER_ASSIGNED) {
            throw new InvalidTripTransitionException("Chỉ có thể xác nhận chuyến đã được nhà cung cấp phân công");
        }

        Instant now = clock.instant();
        booking.setDriverConfirmedAt(now);
        booking.setStatus(BookingStatus.DRIVER_CONFIRMED);
        Booking saved = bookingRepository.saveAndFlush(booking);

        log.info(
                "Trip status changed: tripId={}, bookingId={}, tripStatus={}, bookingStatus={}",
                saved.getId(),
                saved.getId(),
                deriveExecutionStatus(saved),
                saved.getStatus()
        );
        return toDetailResponse(saved, getCoverUrl(saved), null);
    }

    @Override
    @Transactional
    public DriverTripDetailResponse arriveAtPickup(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        log.info(
                "Driver {} reports arrival at pickup for trip {}, booking {}, oldTripStatus={}, oldBookingStatus={}",
                driver.getId(),
                booking.getId(),
                booking.getBookingCode(),
                deriveExecutionStatus(booking),
                booking.getStatus()
        );

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể cập nhật chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new InvalidTripTransitionException("Không thể cập nhật chuyến đi đã hoàn thành");
        }
        if (booking.getDriverConfirmedAt() == null && booking.getStatus() != BookingStatus.DRIVER_CONFIRMED) {
            throw new InvalidTripTransitionException("Tài xế phải xác nhận nhận chuyến trước khi báo đã đến");
        }
        if (booking.getActualArrivedAt() != null || booking.getStatus() == BookingStatus.DRIVER_ARRIVED) {
            throw new TripConflictException("Tài xế đã báo đến điểm đón trước đó");
        }
        if (booking.getActualStartAt() != null || booking.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new InvalidTripTransitionException("Chuyến đi đã bắt đầu, không thể báo đến lại");
        }

        Instant now = clock.instant();
        booking.setActualArrivedAt(now);
        booking.setStatus(BookingStatus.DRIVER_ARRIVED);
        Booking saved = bookingRepository.saveAndFlush(booking);

        log.info(
                "Trip status changed: tripId={}, bookingId={}, tripStatus={}, bookingStatus={}",
                saved.getId(),
                saved.getId(),
                deriveExecutionStatus(saved),
                saved.getStatus()
        );
        return toDetailResponse(saved, getCoverUrl(saved), getLatestLocationForBooking(saved.getId()));
    }

    @Override
    @Transactional
    public DriverTripDetailResponse startTrip(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        log.info(
                "Driver {} starts trip {}, booking {}, oldTripStatus={}, oldBookingStatus={}",
                driver.getId(),
                booking.getId(),
                booking.getBookingCode(),
                deriveExecutionStatus(booking),
                booking.getStatus()
        );

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể bắt đầu chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new InvalidTripTransitionException("Không thể bắt đầu chuyến đi đã hoàn thành");
        }
        if (booking.getDriverConfirmedAt() == null && booking.getStatus() != BookingStatus.DRIVER_CONFIRMED && booking.getStatus() != BookingStatus.DRIVER_ARRIVED) {
            throw new InvalidTripTransitionException("Tài xế phải xác nhận nhận chuyến trước khi bắt đầu");
        }
        if (booking.getActualArrivedAt() == null && booking.getStatus() != BookingStatus.DRIVER_ARRIVED) {
            throw new InvalidTripTransitionException("Tài xế phải báo đã đến điểm đón trước khi bắt đầu chuyến");
        }
        if (booking.getActualStartAt() != null || booking.getStatus() == BookingStatus.IN_PROGRESS) {
            throw new TripConflictException("Chuyến đi đã được bắt đầu trước đó");
        }

        Instant now = clock.instant();
        booking.setActualStartAt(now);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        Booking saved = bookingRepository.saveAndFlush(booking);

        log.info(
                "Trip status changed: tripId={}, bookingId={}, tripStatus={}, bookingStatus={}",
                saved.getId(),
                saved.getId(),
                deriveExecutionStatus(saved),
                saved.getStatus()
        );
        return toDetailResponse(saved, getCoverUrl(saved), getLatestLocationForBooking(saved.getId()));
    }

    @Override
    @Transactional
    public DriverTripDetailResponse finishTrip(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        log.info(
                "Driver {} finishes trip {}, booking {}, oldTripStatus={}, oldBookingStatus={}",
                driver.getId(),
                booking.getId(),
                booking.getBookingCode(),
                deriveExecutionStatus(booking),
                booking.getStatus()
        );

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể kết thúc chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new TripConflictException("Chuyến đi đã được kết thúc trước đó");
        }
        if (booking.getStatus() != BookingStatus.IN_PROGRESS || booking.getActualStartAt() == null) {
            throw new InvalidTripTransitionException("Chỉ có thể kết thúc chuyến đi đang thực hiện (IN_PROGRESS)");
        }

        Instant now = clock.instant();
        booking.setActualEndAt(now);
        booking.setStatus(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION);
        if (booking.getProviderConfirmedAt() == null) {
            booking.setProviderConfirmedAt(now);
        }
        Booking saved = bookingRepository.saveAndFlush(booking);

        log.info(
                "Trip status changed: tripId={}, bookingId={}, tripStatus={}, bookingStatus={}",
                saved.getId(),
                saved.getId(),
                deriveExecutionStatus(saved),
                saved.getStatus()
        );
        return toDetailResponse(saved, getCoverUrl(saved), getLatestLocationForBooking(saved.getId()));
    }

    @Override
    @Transactional
    public TripLocationResponse recordLocation(Long currentUserId, String tripIdOrCode, LocationUpdateRequest request) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể cập nhật vị trí cho chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new InvalidTripTransitionException("Không thể cập nhật vị trí cho chuyến đi đã kết thúc");
        }
        if (booking.getDriverConfirmedAt() == null) {
            throw new InvalidTripTransitionException("Tài xế chưa xác nhận nhận chuyến");
        }

        validateCoordinates(request.latitude(), request.longitude(), request.recordedAt());

        Instant now = clock.instant();

        Optional<VehicleLocation> latestOpt = vehicleLocationRepository.findFirstByBooking_IdOrderByReceivedAtDesc(booking.getId());
        if (latestOpt.isPresent()) {
            VehicleLocation latest = latestOpt.get();
            if (latest.getRecordedAt() != null && request.recordedAt().isBefore(latest.getRecordedAt())) {
                log.warn("Discarding out-of-order location for booking {}: recordedAt {} < latest {}",
                        booking.getBookingCode(), request.recordedAt(), latest.getRecordedAt());
                return toLocationResponse(latest, now);
            }
        }

        VehicleLocation location = new VehicleLocation();
        location.setBooking(booking);
        location.setDriver(driver);
        location.setVehicle(booking.getVehicle());
        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setAccuracyMeters(request.accuracyMeters());
        location.setRecordedAt(request.recordedAt());
        location.setReceivedAt(now);

        VehicleLocation saved = vehicleLocationRepository.save(location);
        return toLocationResponse(saved, now);
    }

    @Override
    public TripTrackingResponse getTripTracking(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        return buildTrackingResponse(booking);
    }

    public TripTrackingResponse buildTrackingResponse(Booking booking) {
        Instant now = clock.instant();
        TripLocationResponse locationResponse = getLatestLocationForBooking(booking.getId());

        Double pickupLat = booking.getPickupLatitude() == null ? null : booking.getPickupLatitude().doubleValue();
        Double pickupLon = booking.getPickupLongitude() == null ? null : booking.getPickupLongitude().doubleValue();
        if (pickupLat == null && pickupLon == null
                && booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()) {
            Optional<MapGeocodingService.GeoPoint> geoOpt = mapGeocodingService.geocodeAddress(booking.getPickupAddress());
            if (geoOpt.isPresent()) {
                pickupLat = geoOpt.get().latitude();
                pickupLon = geoOpt.get().longitude();
            }
        }

        TripTrackingResponse.DriverSummary driverSummary = null;
        if (booking.getDriver() != null) {
            Driver d = booking.getDriver();
            String name = (d.getUser() != null) ? d.getUser().getFullName() : null;
            String phone = (d.getUser() != null) ? d.getUser().getPhone() : null;
            driverSummary = new TripTrackingResponse.DriverSummary(
                    d.getId(),
                    name,
                    phone,
                    d.getLicenseNo(),
                    d.getPortraitUrl()
            );
        }

        TripTrackingResponse.VehicleSummary vehicleSummary = null;
        if (booking.getVehicle() != null) {
            Vehicle v = booking.getVehicle();
            String vName = ((v.getBrand() != null ? v.getBrand() : "") + " " + (v.getModel() != null ? v.getModel() : "")).trim();
            vehicleSummary = new TripTrackingResponse.VehicleSummary(
                    v.getId(),
                    vName,
                    v.getBrand(),
                    v.getModel(),
                    v.getLicensePlate()
            );
        }

        String executionStatus = deriveExecutionStatus(booking);

        return new TripTrackingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                executionStatus,
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getPickupTime(),
                booking.getPickupAddress(),
                pickupLat,
                pickupLon,
                locationResponse,
                driverSummary,
                vehicleSummary
        );
    }

    public static String deriveExecutionStatus(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            return "CANCELLED";
        }
        if (booking.getStatus() == BookingStatus.REJECTED) {
            return "REJECTED";
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            return "COMPLETED";
        }
        if (booking.getStatus() == BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION) {
            return "AWAITING_EXTRA_CHARGE_CONFIRMATION";
        }
        if (booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getActualStartAt() != null) {
            return "IN_PROGRESS";
        }
        if (booking.getStatus() == BookingStatus.DRIVER_ARRIVED || booking.getActualArrivedAt() != null) {
            return "ARRIVED";
        }
        if (booking.getStatus() == BookingStatus.DRIVER_CONFIRMED || booking.getDriverConfirmedAt() != null) {
            return "CONFIRMED";
        }
        if (booking.getStatus() == BookingStatus.DRIVER_ASSIGNED || booking.getDriver() != null) {
            return "ASSIGNED";
        }
        return (booking.getStatus() != null) ? booking.getStatus().name() : "PENDING";
    }

    public TripLocationResponse getLatestLocationForBooking(Long bookingId) {
        Instant now = clock.instant();
        return vehicleLocationRepository.findFirstByBooking_IdOrderByReceivedAtDesc(bookingId)
                .map(loc -> toLocationResponse(loc, now))
                .orElse(new TripLocationResponse(null, null, null, null, null, false, "UNAVAILABLE"));
    }

    private TripLocationResponse toLocationResponse(VehicleLocation loc, Instant now) {
        boolean isStale = Duration.between(loc.getReceivedAt(), now).compareTo(STALE_LOCATION_THRESHOLD) > 0;
        String trackingState = isStale ? "STALE" : "LIVE";
        return new TripLocationResponse(
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getAccuracyMeters(),
                loc.getRecordedAt(),
                loc.getReceivedAt(),
                isStale,
                trackingState
        );
    }

    private void validateCoordinates(Double latitude, Double longitude, Instant recordedAt) {
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Vĩ độ (latitude) không hợp lệ [-90, 90]");
        }
        if (longitude == null || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Kinh độ (longitude) không hợp lệ [-180, 180]");
        }
        Instant now = clock.instant();
        if (recordedAt != null && recordedAt.isAfter(now.plus(MAX_FUTURE_TOLERANCE))) {
            throw new IllegalArgumentException("Thời điểm ghi nhận không thể ở quá xa trong tương lai");
        }
    }

    private String getCoverUrl(Booking booking) {
        if (booking.getVehicle() != null && booking.getVehicle().getId() != null) {
            return vehicleService.getCoverImageUrl(booking.getVehicle().getId());
        }
        return null;
    }

    private Driver resolveDriver(Long currentUserId) {
        Driver driver = userService.findDriverProfile(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("User is not registered as a driver"));

        if (driver.getStatus() != DriverStatus.APPROVED
                || (driver.getUser() != null && driver.getUser().getStatus() != UserStatus.ACTIVE)) {
            throw new AccessDeniedException("Driver account is not active or approved");
        }
        return driver;
    }

    private Booking resolveDriverBooking(Driver driver, String tripIdOrCode) {
        if (tripIdOrCode == null || tripIdOrCode.isBlank()) {
            throw new BookingNotFoundException("Trip identifier is invalid");
        }
        String identifier = tripIdOrCode.trim();
        Booking booking = null;
        try {
            Long id = Long.parseLong(identifier);
            booking = bookingRepository.findById(id)
                    .orElseGet(() -> bookingRepository.findByBookingCode(identifier).orElse(null));
        } catch (NumberFormatException ex) {
            booking = bookingRepository.findByBookingCode(identifier).orElse(null);
        }

        if (booking == null) {
            throw new BookingNotFoundException("Trip not found: " + identifier);
        }

        if (booking.getDriver() == null || !booking.getDriver().getId().equals(driver.getId())) {
            log.warn("Access denied: Driver {} attempted to access trip {} belonging to driver {}",
                    driver.getId(), identifier, booking.getDriver() != null ? booking.getDriver().getId() : "NONE");
            throw new AccessDeniedException("Bạn không có quyền thao tác trên chuyến đi này");
        }

        return booking;
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    private TripView parseView(String view) {
        if (view == null || view.isBlank() || "ALL".equalsIgnoreCase(view.trim())) {
            return null;
        }
        try {
            return TripView.valueOf(view.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid view: " + view + ". Allowed values: TODAY, UPCOMING, HISTORY, ACTIVE");
        }
    }

    private Collection<BookingStatus> parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        String clean = status.trim().toUpperCase(Locale.ROOT);
        if ("ASSIGNED".equals(clean)) {
            return List.of(BookingStatus.DRIVER_ASSIGNED);
        }
        try {
            return List.of(BookingStatus.valueOf(clean));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format: " + date + ". Expected YYYY-MM-DD");
        }
    }

    private Sort resolveSort(String sort, TripView view) {
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0].trim();
            Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            String canonicalProperty = property.toLowerCase(Locale.ROOT);
            if (!ALLOWED_SORT_PROPERTIES.contains(canonicalProperty)) {
                throw new IllegalArgumentException("Invalid sort property: " + property);
            }

            String actualFieldName = switch (canonicalProperty) {
                case "pickuptime" -> "pickupTime";
                case "createdat" -> "createdAt";
                case "totalamount" -> "totalAmount";
                case "status" -> "status";
                default -> "id";
            };

            return Sort.by(direction, actualFieldName).and(Sort.by(direction, "id"));
        }

        if (view == TripView.TODAY || view == TripView.UPCOMING || view == TripView.ACTIVE) {
            return Sort.by(Sort.Order.asc("pickupTime"), Sort.Order.asc("id"));
        }
        return Sort.by(Sort.Order.desc("pickupTime"), Sort.Order.desc("id"));
    }

    private Map<Long, String> getCoverUrlsForBookings(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> vehicleIds = bookings.stream()
                .filter(b -> b.getVehicle() != null && b.getVehicle().getId() != null)
                .map(b -> b.getVehicle().getId())
                .collect(Collectors.toSet());
        return vehicleService.getCoverImageUrls(vehicleIds);
    }

    private DriverTripResponse toResponse(Booking booking, String coverUrl) {
        Vehicle vehicle = booking.getVehicle();
        DriverTripResponse.VehicleSummary vehicleSummary = null;
        if (vehicle != null) {
            String vehicleName = (vehicle.getBrand() != null ? vehicle.getBrand() : "")
                    + " " + (vehicle.getModel() != null ? vehicle.getModel() : "");
            vehicleSummary = new DriverTripResponse.VehicleSummary(
                    vehicle.getId(),
                    vehicleName.trim(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getLicensePlate(),
                    vehicle.getColor(),
                    vehicle.getSeatCount(),
                    coverUrl
            );
        }

        DriverTripResponse.CustomerSummary customerSummary = null;
        if (booking.getRenter() != null && booking.getRenter().getUser() != null) {
            User renterUser = booking.getRenter().getUser();
            String name = renterUser.getFullName() != null && !renterUser.getFullName().isBlank()
                    ? renterUser.getFullName()
                    : "Khách hàng";
            customerSummary = new DriverTripResponse.CustomerSummary(
                    name,
                    renterUser.getPhone()
            );
        }

        String rentalType = "Theo ngày";
        String rentalDuration = "1 ngày";
        if (booking.getPlan() != null) {
            rentalType = determineRentalType(booking.getPlan().getPlanType());
            rentalDuration = calculateRentalDuration(booking);
        }

        String executionStatus = deriveExecutionStatus(booking);

        return new DriverTripResponse(
                booking.getId(),
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getStatus(),
                executionStatus,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                booking.getPassengerCount() != null ? booking.getPassengerCount() : 1,
                vehicleSummary,
                customerSummary,
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                rentalType,
                rentalDuration,
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                executionStatus
        );
    }

    private DriverTripDetailResponse toDetailResponse(Booking booking, String coverUrl, TripLocationResponse latestLocation) {
        Vehicle vehicle = booking.getVehicle();
        DriverTripDetailResponse.VehicleDetail vehicleDetail = null;
        if (vehicle != null) {
            String vehicleName = (vehicle.getBrand() != null ? vehicle.getBrand() : "")
                    + " " + (vehicle.getModel() != null ? vehicle.getModel() : "");
            vehicleDetail = new DriverTripDetailResponse.VehicleDetail(
                    vehicle.getId(),
                    vehicleName.trim(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getLicensePlate(),
                    vehicle.getSeatCount(),
                    vehicle.getTransmission() != null ? vehicle.getTransmission().name() : null,
                    vehicle.getFuelType() != null ? vehicle.getFuelType().name() : null,
                    vehicle.getColor(),
                    coverUrl
            );
        }

        DriverTripDetailResponse.CustomerSummary customerSummary = null;
        if (booking.getRenter() != null && booking.getRenter().getUser() != null) {
            User renterUser = booking.getRenter().getUser();
            String name = renterUser.getFullName() != null && !renterUser.getFullName().isBlank()
                    ? renterUser.getFullName()
                    : "Khách hàng";
            customerSummary = new DriverTripDetailResponse.CustomerSummary(
                    name,
                    renterUser.getPhone(),
                    renterUser.getAvatarUrl()
            );
        }

        DriverTripDetailResponse.ProviderSummary providerSummary = null;
        Provider provider = (vehicle != null) ? vehicle.getProvider() : null;
        if (provider != null) {
            providerSummary = new DriverTripDetailResponse.ProviderSummary(
                    provider.getId(),
                    provider.getDisplayName(),
                    provider.getHotline(),
                    provider.getDepotAddress()
            );
        }

        String rentalType = "Theo ngày";
        String rentalDuration = "1 ngày";
        if (booking.getPlan() != null) {
            rentalType = determineRentalType(booking.getPlan().getPlanType());
            rentalDuration = calculateRentalDuration(booking);
        }

        String executionStatus = deriveExecutionStatus(booking);

        return new DriverTripDetailResponse(
                booking.getId(),
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getStatus(),
                executionStatus,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                booking.getPassengerCount() != null ? booking.getPassengerCount() : 1,
                booking.getContractUrl(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getRenterConfirmedAt(),
                booking.getProviderConfirmedAt(),
                booking.getCancelledAt(),
                booking.getCancelReason(),
                vehicleDetail,
                customerSummary,
                providerSummary,
                rentalType,
                rentalDuration,
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getAssignedAt(),
                booking.getDriverConfirmedAt(),
                booking.getActualArrivedAt(),
                executionStatus,
                latestLocation
        );
    }

    private String determineRentalType(PlanType planType) {
        if (planType == null) return "Theo ngày";
        return switch (planType) {
            case HOURLY -> "Theo giờ";
            case DAILY -> "Theo ngày";
            case MONTHLY -> "Theo tháng";
            case YEARLY -> "Theo năm";
        };
    }

    private String calculateRentalDuration(Booking booking) {
        if (booking.getPlan() == null) return "1 ngày";

        if (booking.getPlan().getSlotPrice() != null
                && booking.getPlan().getSlotPrice().compareTo(BigDecimal.ZERO) > 0
                && booking.getTotalAmount() != null) {
            long slots = booking.getTotalAmount()
                    .divideToIntegralValue(booking.getPlan().getSlotPrice())
                    .longValue();
            if (slots < 1) slots = 1;
            return switch (booking.getPlan().getPlanType()) {
                case HOURLY -> slots + " giờ";
                case DAILY -> slots + " ngày";
                case MONTHLY -> slots + " tháng";
                case YEARLY -> slots + " năm";
            };
        }

        int slotLength = booking.getPlan().getSlotLengthMinutes();
        if (booking.getPlan().getPlanType() == PlanType.HOURLY) {
            return Math.max(1, slotLength / 60) + " giờ";
        } else if (booking.getPlan().getPlanType() == PlanType.DAILY) {
            return Math.max(1, slotLength / 1440) + " ngày";
        } else if (booking.getPlan().getPlanType() == PlanType.MONTHLY) {
            return "1 tháng";
        } else {
            return "1 năm";
        }
    }
}
