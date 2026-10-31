package com.fptufa26.ecomotion.booking.dto.request;

public record BookingQueueFilter(String status, String search, String vehicle, String driver,
                                 String dateFrom, String dateTo, String sort, String action) {
    public BookingQueueFilter(String status, String search, String vehicle, String driver,
            String dateFrom, String dateTo, String sort) {
        this(status, search, vehicle, driver, dateFrom, dateTo, sort, null);
    }
}
