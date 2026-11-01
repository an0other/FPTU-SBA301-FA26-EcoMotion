package com.fptufa26.ecomotion.booking.service;
import com.fptufa26.ecomotion.booking.dto.request.*;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public interface RentalPricingService {
    RentalQuoteResponse quote(RentalQuoteRequest request);
    RentalQuoteResponse.Option validateSelection(Long vehicleId, Instant start, Instant end,
                                                 List<PricingItemRequest> items, BigDecimal expectedAmount);
}
