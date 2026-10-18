package com.fptufa26.ecomotion.booking.exception;

public class BookingContractNotFoundException extends RuntimeException {

    public BookingContractNotFoundException() {
        super("Booking contract not found");
    }
}