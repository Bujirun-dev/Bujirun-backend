package com.bujirun.bujirun.domain.itinerary.generate.exception;

public class InvalidTripDurationException extends IllegalArgumentException {
    public InvalidTripDurationException(String message) {
        super(message);
    }
}