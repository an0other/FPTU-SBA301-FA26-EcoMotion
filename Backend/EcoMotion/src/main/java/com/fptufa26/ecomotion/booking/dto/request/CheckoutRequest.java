package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull PaymentMethod method
) {
}
