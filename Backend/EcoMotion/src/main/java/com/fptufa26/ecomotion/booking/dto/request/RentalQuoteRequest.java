package com.fptufa26.ecomotion.booking.dto.request;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record RentalQuoteRequest(@NotNull Long vehicleId, @NotNull Instant pickupTime,
                                 @NotNull Instant requestedEndAt) {}
