package com.fptufa26.ecomotion.review.exception;

public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException() {
        super("Review not found");
    }
}