package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentDto {
    private Long id;
    private Long bookingId;
    private String bookingCode;
    private PaymentType type;
    private BigDecimal amount;
    private PaymentDirection direction;
    private PaymentMethod method;
    private String gatewayRef;
    private PaymentStatus status;
    private String receiptUrl;
    private Instant paidAt;
    private Instant createdAt;
}
