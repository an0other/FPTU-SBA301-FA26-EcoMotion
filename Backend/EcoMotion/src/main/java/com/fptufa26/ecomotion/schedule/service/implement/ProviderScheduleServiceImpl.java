package com.fptufa26.ecomotion.schedule.service.implement;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.schedule.dto.request.CreateBlockScheduleRequest;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleItemResponse;
import com.fptufa26.ecomotion.schedule.dto.response.ProviderScheduleStatsResponse;
import com.fptufa26.ecomotion.schedule.entity.VehicleSchedule;
import com.fptufa26.ecomotion.schedule.enums.ScheduleStatus;
import com.fptufa26.ecomotion.schedule.enums.ScheduleType;
import com.fptufa26.ecomotion.schedule.repository.VehicleScheduleRepository;
import com.fptufa26.ecomotion.schedule.service.ProviderScheduleService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.service.VehicleCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;

@Service
@RequiredArgsConstructor
public class ProviderScheduleServiceImpl implements ProviderScheduleService {

    private static final DateTimeFormatter VIETNAM_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final VehicleService vehicleService;
    private final VehicleCatalogService vehicleCatalogService;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public long countActiveBlocksByProviderId(Long providerId) {
        if (providerId == null) {
            return 0;
        }
        return vehicleScheduleRepository.countActiveBlocksByProviderId(providerId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long countCurrentlyBlockedVehiclesByProviderId(Long providerId) {
        if (providerId == null) {
            return 0;
        }
        return vehicleScheduleRepository.countCurrentlyBlockedVehiclesByProviderId(providerId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderScheduleStatsResponse getScheduleStats(Long userId) {
        Provider provider = findProviderByUserId(userId);
        Long providerId = provider.getId();
        Instant now = Instant.now();

        long totalActiveBlocks = vehicleScheduleRepository.countActiveBlocksByProviderId(providerId, now);
        long activeMaintenance = vehicleScheduleRepository.countActiveMaintenanceByProviderId(providerId, now);
        long activeBlocked = vehicleScheduleRepository.countActiveBlockedByProviderId(providerId, now);
        long upcomingBookings = vehicleScheduleRepository.countUpcomingBookingsByProviderId(providerId, now);

        return new ProviderScheduleStatsResponse(
                totalActiveBlocks,
                activeMaintenance,
                activeBlocked,
                upcomingBookings
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderScheduleItemResponse> getProviderSchedules(
            Long userId,
            Long vehicleId,
            ScheduleType type,
            ScheduleStatus status,
            Instant fromDate,
            Instant toDate
    ) {
        Provider provider = findProviderByUserId(userId);
        List<VehicleSchedule> schedules = (vehicleId != null)
                ? vehicleScheduleRepository.findVisibleByProviderIdAndVehicleIdOrderDesc(provider.getId(), vehicleId)
                : vehicleScheduleRepository.findVisibleByProviderIdOrderDesc(provider.getId());

        return schedules.stream()
                .filter(s -> {
                    if (type != null && s.getType() != type) {
                        return false;
                    }
                    if (status != null && s.getStatus() != status) {
                        return false;
                    }
                    if (fromDate != null && s.getEndAt().isBefore(fromDate)) {
                        return false;
                    }
                    if (toDate != null && s.getStartAt().isAfter(toDate)) {
                        return false;
                    }
                    return true;
                })
                .map(this::mapToItemResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProviderScheduleItemResponse createBlockSchedule(Long userId, CreateBlockScheduleRequest request) {
        Provider provider = findProviderByUserId(userId);

        if (request.type() == ScheduleType.BOOKED) {
            throw new BadRequestException("Không thể tự tạo lịch loại đơn đặt xe của khách hàng");
        }

        if (request.startAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new BadRequestException("Thời gian bắt đầu không được ở trong quá khứ");
        }

        if (request.endAt().isBefore(request.startAt()) || request.endAt().equals(request.startAt())) {
            throw new BadRequestException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu");
        }

        Vehicle vehicle = vehicleService.findVehicleByIdForUpdate(request.vehicleId())
                .orElseThrow(() -> new NotFoundException("Phương tiện không tồn tại hoặc không thuộc sở hữu của nhà xe"));

        if (!vehicle.getProvider().getId().equals(provider.getId())) {
            throw new NotFoundException("Phương tiện không tồn tại hoặc không thuộc sở hữu của nhà xe");
        }

        if (vehicle.getStatus() == VehicleStatus.PENDING) {
            throw new BadRequestException("Phương tiện đang chờ Admin duyệt hồ sơ, chưa thể tạo lịch chặn hoặc bảo dưỡng");
        }
        if (vehicle.getStatus() == VehicleStatus.REJECTED) {
            throw new BadRequestException("Phương tiện đã bị từ chối phê duyệt, không thể tạo lịch chặn hoặc bảo dưỡng");
        }

        AvailabilityPolicy.Window protectedWindow =
                AvailabilityPolicy.bufferedWindow(
                        vehicle, request.startAt(), request.endAt());
        List<VehicleSchedule> occupiedSchedules = vehicleScheduleRepository.findOccupiedInRange(
                vehicle.getId(),
                protectedWindow.start(),
                protectedWindow.end()
        );

        for (VehicleSchedule occupied : occupiedSchedules) {
            if (occupied.getType() == ScheduleType.BOOKED) {
                String bookingRef = (occupied.getBooking() != null && occupied.getBooking().getBookingCode() != null)
                        ? occupied.getBooking().getBookingCode()
                        : (occupied.getBooking() != null ? String.valueOf(occupied.getBooking().getId()) : "N/A");
                int bufferMinutes = vehicle.getMaintenanceBufferMinutes() != null ? vehicle.getMaintenanceBufferMinutes() : 0;
                String timeRange = VIETNAM_DATETIME_FORMATTER.format(occupied.getStartAt()) + " - " + VIETNAM_DATETIME_FORMATTER.format(occupied.getEndAt());
                String message = bufferMinutes > 0
                        ? String.format("Không thể chặn lịch vì trùng với đơn đặt xe #%s (%s, kèm %d phút đệm kỹ thuật)", bookingRef, timeRange, bufferMinutes)
                        : String.format("Không thể chặn lịch vì trùng với đơn đặt xe #%s (%s)", bookingRef, timeRange);
                throw new ConflictException(message);
            } else if (occupied.getType() == ScheduleType.MAINTENANCE || occupied.getType() == ScheduleType.BLOCKED) {
                if (occupied.getStartAt().isBefore(request.endAt()) && occupied.getEndAt().isAfter(request.startAt())) {
                    String typeName = occupied.getType() == ScheduleType.MAINTENANCE ? "bảo dưỡng" : "chặn bận";
                    String timeRange = VIETNAM_DATETIME_FORMATTER.format(occupied.getStartAt()) + " - " + VIETNAM_DATETIME_FORMATTER.format(occupied.getEndAt());
                    String message = String.format("Không thể chặn lịch vì trùng với lịch %s hiện có (%s)", typeName, timeRange);
                    throw new ConflictException(message);
                }
            }
        }

        VehicleSchedule schedule = new VehicleSchedule();
        schedule.setVehicle(vehicle);
        schedule.setType(request.type());
        schedule.setStartAt(request.startAt());
        schedule.setEndAt(request.endAt());
        schedule.setStatus(ScheduleStatus.ACTIVE);
        schedule.setReason(request.reason() != null ? request.reason().trim() : null);
        schedule.setBooking(null);
        schedule.setExtension(false);

        VehicleSchedule saved = vehicleScheduleRepository.save(schedule);
        return mapToItemResponse(saved);
    }

    @Override
    @Transactional
    public ProviderScheduleItemResponse cancelBlockSchedule(Long userId, Long scheduleId) {
        Provider provider = findProviderByUserId(userId);

        VehicleSchedule schedule = vehicleScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NotFoundException("Lịch xe không tồn tại trên hệ thống"));

        if (!schedule.getVehicle().getProvider().getId().equals(provider.getId())) {
            throw new BadRequestException("Bạn không có quyền quản lý lịch của phương tiện này");
        }

        if (schedule.getType() == ScheduleType.BOOKED) {
            throw new BadRequestException("Không thể hủy lịch đặt xe của khách từ màn hình chặn lịch. Vui lòng xử lý qua luồng Quản lý đơn thuê!");
        }

        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BadRequestException("Lịch xe này đã được mở lại từ trước");
        }

        if (!schedule.getEndAt().isAfter(Instant.now())) {
            throw new BadRequestException("Lịch chặn này đã kết thúc, hệ thống đã tự động mở lại lịch xe");
        }

        schedule.setStatus(ScheduleStatus.CANCELLED);
        VehicleSchedule updated = vehicleScheduleRepository.save(schedule);
        return mapToItemResponse(updated);
    }

    private Provider findProviderByUserId(Long userId) {
        return userService.findProviderByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin đối tác nhà xe cho tài khoản này"));
    }

    private ProviderScheduleItemResponse mapToItemResponse(VehicleSchedule s) {
        Long bookingId = s.getBooking() != null ? s.getBooking().getId() : null;
        String bookingCode = s.getBooking() != null ? s.getBooking().getBookingCode() : null;

        return new ProviderScheduleItemResponse(
                s.getId(),
                s.getVehicle().getId(),
                s.getVehicle().getLicensePlate(),
                s.getVehicle().getBrand(),
                s.getVehicle().getModel(),
                s.getType(),
                s.getStartAt(),
                s.getEndAt(),
                s.getStatus(),
                s.getReason(),
                bookingId,
                bookingCode,
                s.getCreatedAt()
        );
    }
}
