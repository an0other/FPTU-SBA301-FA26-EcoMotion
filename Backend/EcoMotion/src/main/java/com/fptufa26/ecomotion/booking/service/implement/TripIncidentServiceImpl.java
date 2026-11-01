package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.IncidentReportRequest;
import com.fptufa26.ecomotion.booking.dto.request.ResolveIncidentRequest;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.TripIncident;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripIncidentStatus;
import com.fptufa26.ecomotion.booking.enums.TripIncidentUrgency;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.exception.InvalidTripTransitionException;
import com.fptufa26.ecomotion.booking.exception.TripConflictException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.TripIncidentRepository;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.booking.service.TripIncidentService;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripIncidentServiceImpl implements TripIncidentService {

    private final TripIncidentRepository tripIncidentRepository;
    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final DriverOptimizationService driverOptimizationService;
    private final Clock clock;
    private final com.fptufa26.ecomotion.booking.service.BookingNotificationService bookingNotificationService;

    @Override
    @Transactional
    public TripIncident reportIncident(Long currentUserId, String tripIdOrCode, IncidentReportRequest request) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getCancelledAt() != null) {
            throw new InvalidTripTransitionException("Không thể báo sự cố cho chuyến đi đã bị hủy");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getActualEndAt() != null) {
            throw new InvalidTripTransitionException("Không thể báo sự cố cho chuyến đi đã hoàn thành");
        }

        validateIncidentRequest(request);

        List<TripIncident> openIncidents = tripIncidentRepository.findByBooking_IdAndStatus(booking.getId(), TripIncidentStatus.OPEN);
        boolean isDuplicate = openIncidents.stream().anyMatch(inc ->
                inc.getDriver().getId().equals(driver.getId())
                        && inc.getDescription().equalsIgnoreCase(request.description().trim()));
        if (isDuplicate) {
            throw new TripConflictException("Sự cố tương tự đã được báo cáo và đang mở xử lý");
        }

        Instant now = clock.instant();
        TripIncident incident = new TripIncident();
        incident.setBooking(booking);
        incident.setDriver(driver);
        incident.setIncidentType(request.incidentType());
        incident.setDescription(request.description().trim());
        incident.setUrgency(request.urgency());
        incident.setStatus(TripIncidentStatus.OPEN);
        incident.setLocationLatitude(request.latitude());
        incident.setLocationLongitude(request.longitude());
        incident.setEvidenceUrls(request.evidenceUrls() != null ? request.evidenceUrls().trim() : null);
        incident.setCreatedBy(driver.getUser());
        incident.setCreatedAt(now);

        TripIncident saved = tripIncidentRepository.save(incident);
        log.info("Driver {} reported incident #{} ({}) for booking {}",
                driver.getId(), saved.getId(), request.incidentType(), booking.getBookingCode());

        if (booking.getStatus() == BookingStatus.DRIVER_ASSIGNED || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
            if (request.urgency() == TripIncidentUrgency.CRITICAL || request.urgency() == TripIncidentUrgency.HIGH) {
                log.warn("High urgency incident reported before trip for booking {}. Triggering auto reassign.", booking.getBookingCode());
                driverOptimizationService.triggerAutoReassign(
                        booking.getId(), driver.getId(), "Sự cố trước chuyến: " + request.description()
                );
            }
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripIncident> listIncidentsForDriver(Long currentUserId, String tripIdOrCode) {
        Driver driver = resolveDriver(currentUserId);
        Booking booking = resolveDriverBooking(driver, tripIdOrCode);
        return tripIncidentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId());
    }

    @Override
    @Transactional
    public void resolveIncident(Long currentUserId, String bookingCodeOrId, Long incidentId, ResolveIncidentRequest request) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveProviderBooking(provider, bookingCodeOrId);

        if (request == null || request.resolutionNote() == null || request.resolutionNote().isBlank()) {
            throw new BadRequestException("Ghi chú xử lý là bắt buộc");
        }
        if (request.status() == null) {
            throw new BadRequestException("Trạng thái sau xử lý là bắt buộc");
        }

        TripIncident incident = tripIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố: " + incidentId));

        if (!incident.getBooking().getId().equals(booking.getId())) {
            throw new AccessDeniedException("Sự cố không thuộc đơn thuê này");
        }

        incident.setStatus(request.status());
        incident.setResolutionNote(request.resolutionNote().trim());
        incident.setResolvedBy(provider.getUser());
        incident.setResolvedAt(clock.instant());

        tripIncidentRepository.save(incident);
        bookingNotificationService.changed(booking, "INCIDENT_RESOLVED");
        log.info("Provider {} resolved incident #{} with status {}", provider.getId(), incidentId, request.status());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripIncident> getBookingIncidentsForProvider(Long currentUserId, String bookingCodeOrId) {
        Provider provider = resolveProvider(currentUserId);
        Booking booking = resolveProviderBooking(provider, bookingCodeOrId);
        return tripIncidentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TripIncident getIncidentById(Long incidentId) {
        return tripIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố: " + incidentId));
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

    private Provider resolveProvider(Long currentUserId) {
        return userService.findProviderProfile(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Chỉ nhà xe mới có quyền thực hiện thao tác này"));
    }

    private Booking resolveDriverBooking(Driver driver, String tripIdOrCode) {
        if (tripIdOrCode == null || tripIdOrCode.isBlank()) {
            throw new BookingNotFoundException("Trip identifier is invalid");
        }
        String identifier = tripIdOrCode.trim();
        Booking booking;
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
            throw new AccessDeniedException("Bạn không phải tài xế được phân công cho chuyến đi này");
        }
        return booking;
    }

    private Booking resolveProviderBooking(Provider provider, String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new BookingNotFoundException("Booking identifier is invalid");
        }
        String identifier = bookingCodeOrId.trim();
        Booking booking;
        try {
            Long id = Long.parseLong(identifier);
            booking = bookingRepository.findById(id)
                    .orElseGet(() -> bookingRepository.findByBookingCode(identifier).orElse(null));
        } catch (NumberFormatException ex) {
            booking = bookingRepository.findByBookingCode(identifier).orElse(null);
        }

        if (booking == null) {
            throw new BookingNotFoundException("Booking not found: " + identifier);
        }

        if (booking.getVehicle() == null || booking.getVehicle().getProvider() == null
                || !booking.getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new AccessDeniedException("Đơn thuê không thuộc sở hữu của nhà xe");
        }
        return booking;
    }

    private void validateIncidentRequest(IncidentReportRequest request) {
        if (request == null) {
            throw new BadRequestException("Thông tin sự cố không được để trống");
        }
        if (request.incidentType() == null) {
            throw new BadRequestException("Loại sự cố là bắt buộc");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new BadRequestException("Mô tả sự cố là bắt buộc");
        }
        if (request.urgency() == null) {
            throw new BadRequestException("Mức độ khẩn cấp là bắt buộc");
        }
        if (request.latitude() != null && (request.latitude() < -90.0 || request.latitude() > 90.0)) {
            throw new BadRequestException("Vĩ độ (latitude) không hợp lệ [-90, 90]");
        }
        if (request.longitude() != null && (request.longitude() < -180.0 || request.longitude() > 180.0)) {
            throw new BadRequestException("Kinh độ (longitude) không hợp lệ [-180, 180]");
        }
    }
}
