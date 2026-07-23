package com.bujirun.bujirun.domain.itinerary.generate.exception;

public class OpenAiApiException extends RuntimeException {
    public OpenAiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
