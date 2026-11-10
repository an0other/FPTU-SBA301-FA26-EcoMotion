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

    /** Giá thuê của xe này cho đúng khoảng [start, end) theo gói đang hoạt động; ConflictException nếu không có gói phù hợp. */
    // B1: rentalAmountFor dùng để so giá xe thay thế với giá hợp đồng.
    BigDecimal rentalAmountFor(Long vehicleId, Instant start, Instant end);

    // B1: thêm yearlyPlanPrice để lấy giá xe thay thế khi tính chênh lệch giá.
    /** Giá gói năm đang hoạt động của xe; ConflictException nếu xe chưa có gói năm. */
    BigDecimal yearlyPlanPrice(Long vehicleId);
}
