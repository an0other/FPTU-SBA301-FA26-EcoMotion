package com.fptufa26.ecomotion.booking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException() {
        super("Booking not found");
    }
}