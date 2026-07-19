package com.bujirun.bujirun.domain.itinerary.generate.exception;

public class GroqRateLimitException extends RuntimeException {
    public GroqRateLimitException(String message) {
        super(message);
    }

    public GroqRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}