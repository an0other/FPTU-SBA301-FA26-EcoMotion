package com.fptufa26.ecomotion.booking.mapper;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeResponse;
import com.fptufa26.ecomotion.booking.dto.response.DriverAvailabilityResponse;
import com.fptufa26.ecomotion.booking.dto.response.PaymentTransactionResponse;
import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingDetailResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.vehicle.entity.RentalPlan;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ProviderBookingMapper {

    public ProviderBookingDetailResponse toDetailResponse(
            Booking booking,
            List<BookingCharge> charges,
            List<PaymentTransaction> transactions,
            BookingPolicySnapshot snapshot,
            List<String> vehicleImageUrls
    ) {
        ProviderBookingDetailResponse.RenterInfo renterInfo = mapRenter(booking.getRenter());
        ProviderBookingDetailResponse.VehicleInfo vehicleInfo = mapVehicle(booking.getVehicle(), vehicleImageUrls);
        ProviderBookingDetailResponse.PlanInfo planInfo = mapPlan(booking.getPlan(), snapshot);
        ProviderBookingDetailResponse.TripInfo tripInfo = mapTrip(booking);
        ProviderBookingDetailResponse.DriverInfo driverInfo = mapDriver(booking.getDriver());
        ProviderBookingDetailResponse.PolicySnapshotInfo snapshotInfo = mapSnapshot(snapshot);
        ProviderBookingDetailResponse.CancellationInfo cancelInfo = mapCancellation(booking);

        List<BookingChargeResponse> chargeResponses = (charges != null ? charges : List.<BookingCharge>of())
                .stream()
                .map(this::toChargeResponse)
                .toList();

        List<PaymentTransactionResponse> txResponses = (transactions != null ? transactions : List.<PaymentTransaction>of())
                .stream()
                .map(this::toPaymentResponse)
                .toList();

        BigDecimal baseRentalAmount = calculateBaseRentalAmount(booking, charges, snapshot);
        ProviderBookingDetailResponse.FinancialInfo financialInfo = new ProviderBookingDetailResponse.FinancialInfo(
                baseRentalAmount,
                booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO,
                chargeResponses,
                txResponses
        );

        List<ProviderBookingDetailResponse.TimelineEvent> timeline = buildTimeline(booking, charges, transactions);

        return new ProviderBookingDetailResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                renterInfo,
                vehicleInfo,
                planInfo,
                tripInfo,
                driverInfo,
                financialInfo,
                snapshotInfo,
                cancelInfo,
                timeline
        );
    }

    public BookingChargeResponse toChargeResponse(BookingCharge charge) {
        String createdByName = null;
        if (charge.getCreatedBy() != null) {
            createdByName = charge.getCreatedBy().getFullName();
        }

        return new BookingChargeResponse(
                charge.getId(),
                charge.getChargeType(),
                charge.getDescription(),
                charge.getQuantity(),
                charge.getUnitPrice(),
                charge.getAppliedRate(),
                charge.getAmount(),
                charge.isWaived(),
                charge.getEvidenceUrl(),
                createdByName,
                charge.getCreatedAt()
        );
    }

    public PaymentTransactionResponse toPaymentResponse(PaymentTransaction tx) {
        return new PaymentTransactionResponse(
                tx.getId(),
                tx.getCharge() != null ? tx.getCharge().getId() : null,
                tx.getType(),
                tx.getAmount(),
                tx.getDirection(),
                tx.getMethod(),
                tx.getStatus(),
                tx.getGatewayRef(),
                tx.getPaidAt(),
                tx.getCreatedAt()
        );
    }

    public DriverAvailabilityResponse toDriverAvailabilityResponse(Driver driver, boolean available, String conflictReason) {
        User user = driver.getUser();
        return new DriverAvailabilityResponse(
                driver.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : "",
                user != null ? user.getPhone() : "",
                user != null ? user.getEmail() : "",
                driver.getLicenseNo(),
                driver.getLicenseClass(),
                driver.getLicenseExpiry(),
                driver.getPortraitUrl(),
                driver.getRatingAvg(),
                driver.getRatingCount(),
                available,
                conflictReason
        );
    }

    private ProviderBookingDetailResponse.RenterInfo mapRenter(Renter renter) {
        if (renter == null) {
            return null;
        }
        User user = renter.getUser();
        return new ProviderBookingDetailResponse.RenterInfo(
                renter.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : "",
                user != null ? user.getPhone() : "",
                user != null ? user.getEmail() : "",
                user != null ? user.getAvatarUrl() : null,
                renter.isLicenseVerified()
        );
    }

    private ProviderBookingDetailResponse.VehicleInfo mapVehicle(Vehicle vehicle, List<String> imageUrls) {
        if (vehicle == null) {
            return null;
        }
        return new ProviderBookingDetailResponse.VehicleInfo(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate(),
                vehicle.getSeatCount(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getColor(),
                vehicle.getManufactureYear(),
                imageUrls != null ? imageUrls : List.of()
        );
    }

    private ProviderBookingDetailResponse.PlanInfo mapPlan(RentalPlan plan, BookingPolicySnapshot snapshot) {
        if (snapshot != null) {
            return new ProviderBookingDetailResponse.PlanInfo(
                    plan != null ? plan.getId() : null,
                    snapshot.getPlanType(),
                    snapshot.getSlotLengthMinutes(),
                    snapshot.getSlotPrice(),
                    snapshot.getUpfrontAmount()
            );
        }
        if (plan != null) {
            return new ProviderBookingDetailResponse.PlanInfo(
                    plan.getId(),
                    plan.getPlanType(),
                    plan.getSlotLengthMinutes(),
                    plan.getSlotPrice(),
                    plan.getUpfrontAmount()
            );
        }
        return null;
    }

    private ProviderBookingDetailResponse.TripInfo mapTrip(Booking booking) {
        int slotMinutes = 1440;
        if (booking.getPlan() != null && booking.getPlan().getSlotLengthMinutes() != null) {
            slotMinutes = booking.getPlan().getSlotLengthMinutes();
        }
        Instant estimatedEndTime = booking.getPickupTime() != null ?
                booking.getPickupTime().plus(slotMinutes, ChronoUnit.MINUTES) : null;

        return new ProviderBookingDetailResponse.TripInfo(
                booking.getPickupAddress(),
                booking.getPickupTime(),
                estimatedEndTime,
                booking.getActualStartAt(),
                booking.getActualEndAt(),
                booking.getPassengerCount(),
                booking.getContractUrl(),
                booking.getRenterConfirmedAt(),
                booking.getProviderConfirmedAt()
        );
    }

    private ProviderBookingDetailResponse.DriverInfo mapDriver(Driver driver) {
        if (driver == null) {
            return null;
        }
        User user = driver.getUser();
        return new ProviderBookingDetailResponse.DriverInfo(
                driver.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : "",
                user != null ? user.getPhone() : "",
                driver.getLicenseNo(),
                driver.getLicenseClass(),
                driver.getPortraitUrl(),
                driver.getRatingAvg(),
                driver.getRatingCount()
        );
    }

    private ProviderBookingDetailResponse.PolicySnapshotInfo mapSnapshot(BookingPolicySnapshot s) {
        if (s == null) {
            return null;
        }
        return new ProviderBookingDetailResponse.PolicySnapshotInfo(
                s.getId(),
                s.getPlanType(),
                s.getSlotPrice(),
                s.getSlotLengthMinutes(),
                s.getDepositRatio(),
                s.getUpfrontAmount(),
                s.getAllowedOvertimeMinutes(),
                s.getOvertimeSurchargeRatio(),
                s.getWaitingFreeMinutes(),
                s.getWaitingRate(),
                s.getWeekendSurchargeRatio(),
                s.getFreeRadiusKm(),
                s.getOutOfRadiusRate()
        );
    }

    private ProviderBookingDetailResponse.CancellationInfo mapCancellation(Booking booking) {
        if (booking.getStatus() != BookingStatus.CANCELLED && booking.getCancelReason() == null) {
            return null;
        }
        Long cancelledById = null;
        String cancelledByName = null;
        if (booking.getCancelledBy() != null) {
            cancelledById = booking.getCancelledBy().getId();
            cancelledByName = booking.getCancelledBy().getFullName();
        }
        return new ProviderBookingDetailResponse.CancellationInfo(
                cancelledById,
                cancelledByName,
                booking.getCancelReason(),
                booking.getCancelledAt()
        );
    }

    private BigDecimal calculateBaseRentalAmount(Booking booking, List<BookingCharge> charges, BookingPolicySnapshot snapshot) {
        if (charges != null) {
            for (BookingCharge c : charges) {
                if (c.getChargeType() == ChargeType.BASE_RENTAL && !c.isWaived()) {
                    return c.getAmount();
                }
            }
        }
        if (snapshot != null && snapshot.getSlotPrice() != null) {
            return snapshot.getSlotPrice();
        }
        if (booking.getPlan() != null && booking.getPlan().getSlotPrice() != null) {
            return booking.getPlan().getSlotPrice();
        }
        return booking.getTotalAmount();
    }

    private List<ProviderBookingDetailResponse.TimelineEvent> buildTimeline(
            Booking booking,
            List<BookingCharge> charges,
            List<PaymentTransaction> transactions
    ) {
        List<ProviderBookingDetailResponse.TimelineEvent> events = new ArrayList<>();

        if (booking.getCreatedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Đơn đặt xe được tạo",
                    "Khách hàng đã gửi yêu cầu thuê xe mã " + booking.getBookingCode(),
                    booking.getCreatedAt(),
                    "RENTER"
            ));
        }

        if (booking.getStatus() == BookingStatus.ACCEPTED
                || booking.getStatus() == BookingStatus.CONFIRMED
                || booking.getStatus() == BookingStatus.IN_PROGRESS
                || booking.getStatus() == BookingStatus.COMPLETED) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Nhà xe chấp nhận đơn",
                    "Đơn đã được nhà cung cấp chấp thuận và chuyển sang chờ thanh toán",
                    booking.getCreatedAt().plus(5, ChronoUnit.MINUTES),
                    "PROVIDER"
            ));
        }

        if (booking.getStatus() == BookingStatus.REJECTED) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Nhà xe từ chối đơn",
                    "Nhà cung cấp đã từ chối yêu cầu đặt xe",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : booking.getCreatedAt(),
                    "PROVIDER"
            ));
        }

        if (booking.getStatus() == BookingStatus.CANCELLED && booking.getCancelledAt() != null) {
            String actor = booking.getCancelledBy() != null ? booking.getCancelledBy().getFullName() : "SYSTEM";
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Đơn đã bị hủy",
                    "Lý do: " + (booking.getCancelReason() != null ? booking.getCancelReason().name() : "Không xác định"),
                    booking.getCancelledAt(),
                    actor
            ));
        }

        if (booking.getDriver() != null && booking.getDriver().getUser() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Đã gán tài xế",
                    "Tài xế " + booking.getDriver().getUser().getFullName() + " được phân công phụ trách chuyến đi",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : booking.getCreatedAt(),
                    "PROVIDER"
            ));
        }

        if (transactions != null) {
            for (PaymentTransaction tx : transactions) {
                if (tx.getPaidAt() != null) {
                    events.add(new ProviderBookingDetailResponse.TimelineEvent(
                            "Giao dịch thanh toán: " + tx.getType().name(),
                            "Đã thanh toán " + tx.getAmount() + " VND qua " + tx.getMethod().name(),
                            tx.getPaidAt(),
                            "SYSTEM"
                    ));
                }
            }
        }

        if (charges != null) {
            for (BookingCharge charge : charges) {
                if (charge.getCreatedAt() != null && charge.getChargeType() != ChargeType.BASE_RENTAL) {
                    String creator = charge.getCreatedBy() != null ? charge.getCreatedBy().getFullName() : "PROVIDER";
                    events.add(new ProviderBookingDetailResponse.TimelineEvent(
                            "Phát sinh phụ phí: " + charge.getChargeType().name(),
                            charge.getDescription() + " (Số tiền: " + charge.getAmount() + " VND)",
                            charge.getCreatedAt(),
                            creator
                    ));
                }
            }
        }

        if (booking.getActualStartAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Bắt đầu chuyến đi",
                    "Tài xế đã đón khách tại điểm hẹn và bắt đầu hành trình",
                    booking.getActualStartAt(),
                    "DRIVER"
            ));
        }

        if (booking.getActualEndAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Kết thúc chuyến đi",
                    "Hành trình di chuyển đã hoàn thành",
                    booking.getActualEndAt(),
                    "DRIVER"
            ));
        }

        if (booking.getRenterConfirmedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Khách hàng xác nhận hoàn thành",
                    "Người thuê đã ký nhận hoàn thành chuyến đi",
                    booking.getRenterConfirmedAt(),
                    "RENTER"
            ));
        }

        if (booking.getProviderConfirmedAt() != null) {
            events.add(new ProviderBookingDetailResponse.TimelineEvent(
                    "Nhà xe xác nhận hoàn thành",
                    "Nhà cung cấp đã đối soát và xác nhận hoàn tất",
                    booking.getProviderConfirmedAt(),
                    "PROVIDER"
            ));
        }

        events.sort(Comparator.comparing(ProviderBookingDetailResponse.TimelineEvent::timestamp));
        return events;
    }
}
