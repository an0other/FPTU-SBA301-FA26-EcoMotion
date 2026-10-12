package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderBookingServiceImpl implements ProviderBookingService {

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final com.fptufa26.ecomotion.vehicle.service.VehicleService vehicleService;

    @Override
    public List<ProviderBookingResponse> listBookings(Long currentUserId, String status) {
        Provider provider = userService.findProviderProfile(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Current user has no provider profile"));

        BookingStatus filter = parseStatus(status);
        List<Booking> bookings = filter == null
                ? bookingRepository.findProviderQueue(provider.getId())
                : bookingRepository.findProviderQueueByStatus(provider.getId(), filter);

        return bookings.stream().map(this::toResponse).toList();
    }

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid booking status: " + status);
        }
    }

    private ProviderBookingResponse toResponse(Booking booking) {
        Vehicle vehicle = booking.getVehicle();
        User renterUser = booking.getRenter().getUser();
        String renterName = renterUser.getFullName() != null && !renterUser.getFullName().isBlank()
                ? renterUser.getFullName()
                : renterUser.getEmail();
        String coverUrl = vehicleService.getCoverImageUrl(vehicle.getId());

        return new ProviderBookingResponse(
                booking.getBookingCode(),
                vehicle.getBrand() + " " + vehicle.getModel(),
                coverUrl,
                vehicle.getLicensePlate(),
                renterName,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getStatus(),
                booking.getTotalAmount());
    }
}
