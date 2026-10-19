package com.fptufa26.ecomotion.dispute.service;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.dispute.dto.response.DisputeOpenedResponse;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.user.entity.User;

public interface DisputeService {

    DisputeOpenedResponse open(Booking booking, User raisedBy, DisputeCategory category, BookingCharge charge);
}
