package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.VehicleOperationalRiskService;
import com.fptufa26.ecomotion.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleOperationalRiskServiceImpl implements VehicleOperationalRiskService {

    private static final Set<BookingStatus> VEHICLE_NOT_RETURNED_STATUSES = EnumSet.of(
            BookingStatus.IN_PROGRESS,
            BookingStatus.AWAITING_END_CONFIRMATION,
            BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION,
            BookingStatus.AWAITING_FINAL_PAYMENT
    );

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final Clock clock;

    @Override
    public OperationalRiskResponse assess(Booking booking) {
        if (booking != null && (booking.getCancelledAt() != null || EnumSet.of(BookingStatus.CANCELLED,
                BookingStatus.CANCELLATION_REVIEW, BookingStatus.REJECTED, BookingStatus.COMPLETED).contains(booking.getStatus())))
            return OperationalRiskResponse.safe();
        if (booking == null || booking.getId() == null || booking.getVehicle() == null
                || booking.getVehicle().getId() == null || booking.getPickupTime() == null) {
            return OperationalRiskResponse.safe();
        }
        return bookingRepository.findByVehicle_IdAndStatusIn(
                        booking.getVehicle().getId(), VEHICLE_NOT_RETURNED_STATUSES).stream()
                .filter(other -> !booking.getId().equals(other.getId()))
                .filter(other -> other.getPickupTime() != null
                        && other.getPickupTime().isBefore(booking.getPickupTime()))
                .filter(other -> other.getActualEndAt() == null)
                .min(Comparator.comparing(Booking::getPickupTime).reversed())
                .map(other -> lateness(booking, other))
                .orElseGet(OperationalRiskResponse::safe);
    }

    /**
     * Xe sẵn sàng = max(giờ trả theo lịch của chuyến trước, hiện tại nếu đã quá giờ) + thời gian chuẩn bị xe.
     * Chỉ cảnh báo khi mốc đó vượt giờ nhận của đơn này; chuyến trước trả sớm hơn nhiều thì không báo nhầm.
     */
    private OperationalRiskResponse lateness(Booking booking, Booking previous) {
        Instant plannedEnd = previous.getPlannedEndAt();
        if (plannedEnd == null) return OperationalRiskResponse.vehicleBusy();
        Instant now = clock.instant();
        boolean overdue = now.isAfter(plannedEnd);
        int bufferMinutes = booking.getVehicle().getMaintenanceBufferMinutes() == null
                ? 0 : Math.max(0, booking.getVehicle().getMaintenanceBufferMinutes());
        Instant readyAt = (overdue ? now : plannedEnd).plus(Duration.ofMinutes(bufferMinutes));
        if (!readyAt.isAfter(booking.getPickupTime())) return OperationalRiskResponse.safe();
        long delayMinutes = Math.max(1, Duration.between(booking.getPickupTime(), readyAt).toMinutes());
        return OperationalRiskResponse.vehicleLate(previous.getBookingCode(), plannedEnd, readyAt, delayMinutes, overdue);
    }

    @Override
    public OperationalRiskResponse assessForRenter(Long userId, String bookingCode) {
        String normalized = normalize(bookingCode);
        Booking booking = bookingRepository.findByBookingCodeAndRenter_User_Id(normalized, userId)
                .orElseThrow(() -> new BookingNotFoundException(normalized));
        return assess(booking);
    }

    @Override
    public OperationalRiskResponse assessForProvider(Long userId, String bookingCode) {
        Long providerId = userService.findProviderByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Tài khoản không thuộc provider"))
                .getId();
        String normalized = normalize(bookingCode);
        Booking booking = bookingRepository.findByBookingCodeAndVehicle_Provider_Id(normalized, providerId)
                .orElseThrow(() -> new BookingNotFoundException(normalized));
        return assess(booking);
    }

    private String normalize(String bookingCode) {
        return bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
    }
}
