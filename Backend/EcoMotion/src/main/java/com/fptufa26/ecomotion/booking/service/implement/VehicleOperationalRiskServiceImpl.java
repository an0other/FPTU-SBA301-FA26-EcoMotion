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

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

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

    @Override
    public OperationalRiskResponse assess(Booking booking) {
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
                .min(java.util.Comparator.comparing(Booking::getPickupTime).reversed())
                .map(other -> new OperationalRiskResponse(
                        true,
                        other.getBookingCode(),
                        "Xe đang phục vụ chuyến " + other.getBookingCode()
                                + " và chưa kết thúc; booking kế tiếp có nguy cơ trễ."))
                .orElseGet(OperationalRiskResponse::safe);
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
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
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
