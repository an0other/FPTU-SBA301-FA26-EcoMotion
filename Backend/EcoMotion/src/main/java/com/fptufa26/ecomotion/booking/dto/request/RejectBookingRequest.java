package com.fptufa26.ecomotion.booking.dto.request;

import com.fptufa26.ecomotion.booking.enums.CancelReason;
import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        CancelReason reason,
        @Size(max = 500, message = "Note cannot exceed 500 characters")
        String note
) {
}
