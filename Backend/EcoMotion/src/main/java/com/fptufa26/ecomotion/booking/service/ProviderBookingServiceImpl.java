package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProviderBookingServiceImpl implements ProviderBookingService {

    private final BookingRepository bookingRepository;
    private final ProviderRepository providerRepository;

    public ProviderBookingServiceImpl(BookingRepository bookingRepository, ProviderRepository providerRepository) {
        this.bookingRepository = bookingRepository;
        this.providerRepository = providerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderBookingResponse> getProviderBookings(Long userId, BookingStatus status) {
        Provider provider = providerRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("Provider profile not found for authenticated user"));

        List<Booking> bookings;
        if (status == null) {
            bookings = bookingRepository.findByVehicle_Provider_IdOrderByCreatedAtDesc(provider.getId());
        } else {
            bookings = bookingRepository.findByVehicle_Provider_IdAndStatusOrderByCreatedAtDesc(provider.getId(), status);
        }

        return bookings.stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ProviderBookingResponse mapToResponse(Booking booking) {
        String vehicleName = "";
        String licensePlate = "";
        if (booking.getVehicle() != null) {
            vehicleName = (booking.getVehicle().getBrand() != null ? booking.getVehicle().getBrand() : "")
                    + " "
                    + (booking.getVehicle().getModel() != null ? booking.getVehicle().getModel() : "");
            vehicleName = vehicleName.trim();
            licensePlate = booking.getVehicle().getLicensePlate();
        }

        String renterName = "";
        if (booking.getRenter() != null && booking.getRenter().getUser() != null) {
            renterName = booking.getRenter().getUser().getFullName();
        }

        return new ProviderBookingResponse(
                booking.getBookingCode(),
                vehicleName,
                licensePlate,
                renterName,
                booking.getPickupTime(),
                booking.getPickupAddress(),
                booking.getStatus(),
                booking.getTotalAmount()
        );
    }
}
