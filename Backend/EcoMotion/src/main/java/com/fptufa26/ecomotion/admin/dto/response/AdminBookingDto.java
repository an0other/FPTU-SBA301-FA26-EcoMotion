package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.CancelReason;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingDto {
    private Long id;
    private String bookingCode;
    private Long renterId;
    private String renterName;
    private String renterEmail;
    private String renterPhone;
    private Long vehicleId;
    private String vehicleModel;
    private String vehicleLicensePlate;
    private Long providerId;
    private String providerDisplayName;
    private Long driverId;
    private String driverName;
    private BookingStatus status;
    private String pickupAddress;
    private Instant pickupTime;
    private Integer passengerCount;
    private BigDecimal totalAmount;
    private Instant actualStartAt;
    private Instant actualEndAt;
    private Instant renterConfirmedAt;
    private Instant providerConfirmedAt;
    private CancelReason cancelReason;
    private Instant cancelledAt;
    private List<ChargeDto> charges;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeDto {
        private Long id;
        private ChargeType chargeType;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal appliedRate;
        private BigDecimal amount;
        private boolean isWaived;
    }
}
