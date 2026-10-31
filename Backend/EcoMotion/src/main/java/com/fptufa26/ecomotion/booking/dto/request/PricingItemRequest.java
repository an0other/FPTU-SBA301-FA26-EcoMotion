package com.fptufa26.ecomotion.booking.dto.request;
import jakarta.validation.constraints.*;
public record PricingItemRequest(@NotNull Long planId, @NotNull @Min(1) @Max(8640) Integer quantity) {}
