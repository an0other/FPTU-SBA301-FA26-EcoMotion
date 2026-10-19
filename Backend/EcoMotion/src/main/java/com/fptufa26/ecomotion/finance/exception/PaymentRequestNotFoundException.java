package com.fptufa26.ecomotion.finance.exception;

public class PaymentRequestNotFoundException extends RuntimeException {
    public PaymentRequestNotFoundException(String message) {
        super(message);
    }
}
