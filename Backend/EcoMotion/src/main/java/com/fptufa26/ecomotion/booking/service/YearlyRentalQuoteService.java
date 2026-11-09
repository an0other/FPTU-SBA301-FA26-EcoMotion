package com.fptufa26.ecomotion.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface YearlyRentalQuoteService {
    void validatePrice(BigDecimal total);

    Quote quote(Long userId, String bookingCode);

    record Month(int number, LocalDate from, LocalDate until, BigDecimal rent) {}
    record Quote(String bookingCode, String status, LocalDate plannedStart,
                 LocalDate plannedEnd, BigDecimal totalRent, BigDecimal roundingUnit,
                 BigDecimal averageMonth, BigDecimal maximumEarlyTerminationFee,
                 List<Month> months, String scope) {}
}
