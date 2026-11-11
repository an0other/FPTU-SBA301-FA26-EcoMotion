package com.fptufa26.ecomotion.user.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProviderApplicationRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Pattern(regexp = "^0[0-9]{9,10}$") String hotline,
        @NotBlank @Size(max = 500) String depotAddress,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
        @Size(max = 20) String bankCode,
        @Pattern(regexp = "^$|^[0-9]{6,30}$") String bankAccountNo,
        @Size(max = 150) String bankAccountHolder) {
}
