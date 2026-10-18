package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingReviewContext;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.BookingReviewAccessService;
import com.fptufa26.ecomotion.user.entity.Driver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingReviewAccessServiceImpl
        implements BookingReviewAccessService {

    private final BookingRepository bookingRepository;

    @Override
    public BookingReviewContext getOwnedBooking(
            Long userId,
            String bookingCode) {
        Booking booking = bookingRepository
                .findByBookingCodeAndRenter_User_Id(bookingCode, userId)
                .orElseThrow(BookingNotFoundException::new);

        Driver driver = booking.getDriver();

        return new BookingReviewContext(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getVehicle().getId(),
                booking.getVehicle().getProvider().getId(),
                driver == null ? null : driver.getId(),
                driver == null ? null : driver.getProvider().getId());
    }
}