package com.fptufa26.ecomotion.booking.mapper;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingDetailResponse;
import com.fptufa26.ecomotion.booking.dto.response.BookingSummaryResponse;
import com.fptufa26.ecomotion.booking.dto.response.CancelBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.CheckoutResultResponse;
import com.fptufa26.ecomotion.booking.dto.response.ConfirmEndResponse;
import com.fptufa26.ecomotion.booking.dto.response.CreateBookingResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverSnippetResponse;
import com.fptufa26.ecomotion.booking.dto.response.RefundPreviewResponse;
import com.fptufa26.ecomotion.booking.dto.response.TimelineStepResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    public BookingSummaryResponse toSummary(Booking booking, PlanType planType, String imageUrl) {
        Vehicle vehicle = booking.getVehicle();
        return new BookingSummaryResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                planType,
                vehicle.getBrand() + " " + vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getLicensePlate(),
                imageUrl,
                booking.getPickupAddress(),
                booking.getPickupTime(),
                booking.getPassengerCount(),
                booking.getTotalAmount());
    }

    public BookingChargeLineResponse toChargeLine(BookingCharge charge) {
        return new BookingChargeLineResponse(
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount());
    }

    public DriverSnippetResponse toDriver(Driver driver) {
        if (driver == null) {
            return null;
        }
        User user = driver.getUser();
        return new DriverSnippetResponse(
                user.getFullName(),
                user.getPhone(),
                driver.getPortraitUrl(),
                driver.getRatingAvg(),
                driver.getRatingCount() == null ? 0 : driver.getRatingCount());
    }

    public BookingDetailResponse toDetail(Booking booking,
                                          PlanType planType,
                                          String imageUrl,
                                          Instant endTime,
                                          int slotCount,
                                          List<BookingChargeLineResponse> charges,
                                          List<TimelineStepResponse> timeline,
                                          RefundPreviewResponse refundPreview,
                                          boolean canConfirmEnd,
                                          List<DisputeCategory> disputeCategories) {
        Vehicle vehicle = booking.getVehicle();
        return new BookingDetailResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                planType,
                vehicle.getBrand() + " " + vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getLicensePlate(),
                imageUrl,
                booking.getPickupAddress(),
                booking.getPickupTime(),
                endTime,
                booking.getPassengerCount(),
                slotCount,
                booking.getTotalAmount(),
                booking.getCreatedAt(),
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getRenterConfirmedAt(),
                booking.getProviderConfirmedAt(),
                booking.getCancelReason(),
                booking.getCancelledAt(),
                toDriver(booking.getDriver()),
                charges,
                timeline,
                refundPreview,
                canConfirmEnd,
                disputeCategories);
    }

    public CancelBookingResponse toCancel(Booking booking, BigDecimal refundAmount) {
        return new CancelBookingResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCancelReason(),
                booking.getCancelledAt(),
                refundAmount);
    }

    public ConfirmEndResponse toConfirmEnd(Booking booking) {
        return new ConfirmEndResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getRenterConfirmedAt(),
                booking.getStatus() == BookingStatus.COMPLETED);
    }
}
