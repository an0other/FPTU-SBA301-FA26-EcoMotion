package com.fptufa26.ecomotion.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RespondOperationalProposalRequest(
        @NotBlank @Pattern(regexp = "ACCEPT|REJECT") String action,
        @Size(max = 500) String reason) {
}
