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
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.OperationalResolutionType;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookingMapper {

    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "totalAmount", source = "booking.totalAmount")
    @Mapping(target = "depositAmount", source = "depositAmount")
    @Mapping(target = "pickupTime", source = "booking.pickupTime")
    @Mapping(target = "endTime", source = "endTime")
    @Mapping(target = "baseRental", source = "charge")
    CreateBookingResponse toCreateBookingResponse(Booking booking, Instant endTime, BookingCharge charge,
                                                  BigDecimal depositAmount);

    @Mapping(target = "createdByName", source = "createdBy.fullName")
    BookingChargeResponse toChargeResponse(BookingCharge charge);

    default BookingChargeLineResponse toChargeLine(BookingCharge charge) {
        return new BookingChargeLineResponse(
                charge.getId(),
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount(),
                charge.isWaived(),
                charge.isWaived() ? BigDecimal.ZERO : charge.getAmount());
    }

    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "ratingCount", source = "ratingCount", qualifiedByName = "nullToZero")
    DriverSnippetResponse toDriver(Driver driver);

    @Named("nullToZero")
    default int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    default CheckoutPreviewResponse toCheckoutPreview(Booking booking,
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

    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "paymentType", source = "payment.type")
    @Mapping(target = "amountPaid", source = "payment.amount")
    @Mapping(target = "endTime", source = "endTime")
    CheckoutResultResponse toCheckoutResult(Booking booking, PaymentTransaction payment, Instant endTime);

    default BookingSummaryResponse toSummary(
            Booking booking,
            BookingPolicySnapshot snapshot,
            String imageUrl,
            boolean canPay,
            boolean atRisk,
            String riskMessage
    ) {
        Vehicle vehicle = booking.getVehicle();
        boolean vehicleReplaced = booking.getOperationalResolution() == OperationalResolutionType.CHANGE_VEHICLE;
        PlanType planType = snapshot != null ? snapshot.getPlanType() : booking.getPlan().getPlanType();
        String vehicleName = !vehicleReplaced && snapshot != null && snapshot.getVehicleName() != null
                ? snapshot.getVehicleName()
                : vehicle.getBrand() + " " + vehicle.getModel();
        int manufactureYear = !vehicleReplaced && snapshot != null && snapshot.getVehicleManufactureYear() != null
                ? snapshot.getVehicleManufactureYear()
                : vehicle.getManufactureYear();
        String licensePlate = !vehicleReplaced && snapshot != null && snapshot.getVehicleLicensePlate() != null
                ? snapshot.getVehicleLicensePlate()
                : vehicle.getLicensePlate();
        String snapshotImage = !vehicleReplaced && snapshot != null && snapshot.getVehicleImageUrl() != null
                ? snapshot.getVehicleImageUrl()
                : imageUrl;
        return new BookingSummaryResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                planType,
                vehicleName,
                manufactureYear,
                licensePlate,
                snapshotImage,
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                booking.getPickupTime(),
                booking.getPassengerCount(),
                booking.getTotalAmount(),
                canPay,
                atRisk,
                riskMessage);
    }

    default BookingDetailResponse toDetail(Booking booking,
                                           BookingPolicySnapshot snapshot,
                                           String imageUrl,
                                           Instant endTime,
                                           int slotCount,
                                           List<BookingChargeLineResponse> charges,
                                           List<TimelineStepResponse> timeline,
                                           RefundPreviewResponse refundPreview,
                                           boolean canCancel,
                                           boolean canEditRequest,
                                           boolean canPay,
                                           boolean canConfirmStart,
                                           boolean canConfirmEnd,
                                           List<DisputeCategory> disputeCategories) {
        Vehicle vehicle = booking.getVehicle();
        boolean vehicleReplaced = booking.getOperationalResolution() == OperationalResolutionType.CHANGE_VEHICLE;
        String vehicleName = !vehicleReplaced && snapshot.getVehicleName() != null
                ? snapshot.getVehicleName()
                : vehicle.getBrand() + " " + vehicle.getModel();
        int manufactureYear = !vehicleReplaced && snapshot.getVehicleManufactureYear() != null
                ? snapshot.getVehicleManufactureYear()
                : vehicle.getManufactureYear();
        String licensePlate = !vehicleReplaced && snapshot.getVehicleLicensePlate() != null
                ? snapshot.getVehicleLicensePlate()
                : vehicle.getLicensePlate();
        String snapshotImage = !vehicleReplaced && snapshot.getVehicleImageUrl() != null
                ? snapshot.getVehicleImageUrl()
                : imageUrl;
        return new BookingDetailResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                snapshot.getPlanType(),
                vehicle.getId(),
                vehicleName,
                manufactureYear,
                licensePlate,
                snapshotImage,
                booking.getPickupAddress(),
                booking.getPickupLatitude(),
                booking.getPickupLongitude(),
                booking.getDriverNote(),
                booking.getPickupTime(),
                endTime,
                booking.getPassengerCount(),
                slotCount,
                booking.getTotalAmount(),
                snapshot.getDepositAmount(),
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
                canCancel,
                canEditRequest,
                canPay,
                canConfirmStart,
                canConfirmEnd,
                disputeCategories);
    }

    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "cancelReason", source = "booking.cancelReason")
    @Mapping(target = "cancelledAt", source = "booking.cancelledAt")
    @Mapping(target = "refundAmount", source = "refundAmount")
    CancelBookingResponse toCancel(Booking booking, BigDecimal refundAmount);

    default ConfirmEndResponse toConfirmEnd(Booking booking) {
        return new ConfirmEndResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getRenterConfirmedAt(),
                booking.getDriverEndConfirmedAt(),
                booking.getActualEndAt(),
                booking.getStatus() == BookingStatus.COMPLETED);
    }
}
