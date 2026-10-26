package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.OperationalRiskResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;

public interface VehicleOperationalRiskService {

    OperationalRiskResponse assess(Booking booking);

    OperationalRiskResponse assessForRenter(Long userId, String bookingCode);

    OperationalRiskResponse assessForProvider(Long userId, String bookingCode);
}
