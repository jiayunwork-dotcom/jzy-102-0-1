package com.example.terrain.validation;

/**
 * Thrown when request parameters are invalid. Mapped to an HTTP 400 response by
 * {@link GlobalExceptionHandler} before any terrain computation runs.
 */
public class InvalidParamsException extends RuntimeException {
    public InvalidParamsException(String message) {
        super(message);
    }
}
