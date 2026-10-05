package com.fptufa26.ecomotion.booking.mapper;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BookingMapper {

    public CreateBookingResponse toCreateBookingResponse(Booking booking, Instant endTime, BookingCharge charge) {
        return new CreateBookingResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getTotalAmount(),
                booking.getPickupTime(),
                endTime,
                toChargeResponse(charge));
    }

    public BookingChargeResponse toChargeResponse(BookingCharge charge) {
        return new BookingChargeResponse(
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAmount());
    }
}
