package com.fptufa26.ecomotion.booking.service;

import java.time.Instant;

public interface BookingHoldExpiryService {

    void expireDueHolds(Instant now);
}
