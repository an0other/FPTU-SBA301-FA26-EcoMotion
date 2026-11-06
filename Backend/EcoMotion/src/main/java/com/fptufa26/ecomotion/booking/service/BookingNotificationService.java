package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;

import java.math.BigDecimal;

public interface BookingNotificationService {
    void changed(Booking booking, String event);

    /** Tài xế bắt đầu chuyến: Renter nhận TRIP_STARTED; Provider và Tài xế nhận BOOKING_CHANGED để đồng bộ. */
    void tripStarted(Booking booking);

    /** Tài xế kết thúc chuyến: Renter nhận TRIP_COMPLETED; Provider và Tài xế nhận BOOKING_CHANGED để đồng bộ. */
    void tripEnded(Booking booking);

    /** Gửi đúng một thông báo cho mỗi bên: Tài xế mới nhận DRIVER_ASSIGNED; Provider và Renter nhận BOOKING_CHANGED. */
    void driverAssigned(Booking booking);
    void bookingPlaced(Booking booking, BigDecimal depositAmount);

    void depositPaid(Booking booking, BigDecimal depositAmount);
}
