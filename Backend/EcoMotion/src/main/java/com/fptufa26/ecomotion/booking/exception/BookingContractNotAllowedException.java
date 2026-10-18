package com.fptufa26.ecomotion.booking.exception;

public class BookingContractNotAllowedException extends RuntimeException {

    public BookingContractNotAllowedException(String message) {
        super(message);
    }
}