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
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderScheduleServiceImpl implements ProviderScheduleService {

    private final VehicleScheduleRepository vehicleScheduleRepository;
    private final VehicleService vehicleService;
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
        List<VehicleSchedule> schedules = vehicleScheduleRepository.findByProviderIdOrderDesc(provider.getId());

        return schedules.stream()
                .filter(s -> {
                    if (vehicleId != null && !s.getVehicle().getId().equals(vehicleId)) {
                        return false;
                    }
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

        // Không cho phép lịch bắt đầu trong quá khứ (cho phép sai số 60 giây do độ trễ truyền gói mạng)
        if (request.startAt().isBefore(Instant.now().minusSeconds(60))) {
            throw new BadRequestException("Thời gian bắt đầu không được ở trong quá khứ");
        }

        if (request.endAt().isBefore(request.startAt()) || request.endAt().equals(request.startAt())) {
            throw new BadRequestException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu");
        }

        Vehicle vehicle = vehicleService.findVehicleByIdAndProviderId(request.vehicleId(), provider.getId())
                .orElseThrow(() -> new NotFoundException("Phương tiện không tồn tại hoặc không thuộc sở hữu của nhà xe"));

        // Kiểm tra chống trùng với đơn đặt của khách hàng
        boolean hasConflictBooking = vehicleScheduleRepository.existsActiveBookingOverlap(
                vehicle.getId(),
                request.startAt(),
                request.endAt()
        );
        if (hasConflictBooking) {
            throw new ConflictException("Không thể chặn lịch: Xe đã có đơn đặt của khách hàng trong khoảng thời gian này!");
        }

        // Kiểm tra chống trùng với lịch bảo trì hoặc việc riêng khác đang có hiệu lực
        boolean hasConflictBlock = vehicleScheduleRepository.existsActiveBlockOverlap(
                vehicle.getId(),
                request.startAt(),
                request.endAt()
        );
        if (hasConflictBlock) {
            throw new ConflictException("Xe đã có một lịch bảo dưỡng hoặc chặn bận khác đang hiệu lực trong khoảng thời gian này!");
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
