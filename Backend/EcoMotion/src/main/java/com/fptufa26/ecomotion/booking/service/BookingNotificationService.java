package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.entity.Booking;

import java.math.BigDecimal;

public interface BookingNotificationService {
    void changed(Booking booking, String event);
    void bookingPlaced(Booking booking, BigDecimal depositAmount);

    void depositPaid(Booking booking, BigDecimal depositAmount);
}
