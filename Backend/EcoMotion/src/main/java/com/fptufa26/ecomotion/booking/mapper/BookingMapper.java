package com.fptufa26.ecomotion.booking.mapper;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    public CheckoutPreviewResponse toCheckoutPreview(Booking booking,
                                                     BookingPolicySnapshot snapshot,
                                                     String imageUrl,
                                                     Instant endTime,
                                                     int slotCount,
                                                     BigDecimal amountDue) {
        Vehicle vehicle = booking.getVehicle();
        boolean upfront = snapshot.getPlanType() == PlanType.YEARLY;
        return new CheckoutPreviewResponse(
                booking.getBookingCode(),
                vehicle.getBrand() + " " + vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getLicensePlate(),
                imageUrl,
                booking.getPickupAddress(),
                booking.getPickupTime(),
                endTime,
                booking.getPassengerCount(),
                slotCount,
                snapshot.getPlanType(),
                booking.getTotalAmount(),
                snapshot.getDepositRatio(),
                amountDue,
                upfront);
    }

    public CheckoutResultResponse toCheckoutResult(Booking booking, PaymentTransaction payment, Instant endTime) {
        return new CheckoutResultResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                payment.getType(),
                payment.getAmount(),
                endTime);
    }
}
