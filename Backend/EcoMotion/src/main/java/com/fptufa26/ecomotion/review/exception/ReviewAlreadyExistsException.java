package com.fptufa26.ecomotion.review.exception;

public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException() {
        super("A review for this booking target already exists");
    }
}