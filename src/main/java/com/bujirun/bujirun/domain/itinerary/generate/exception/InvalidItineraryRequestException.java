package com.bujirun.bujirun.domain.itinerary.generate.exception;

public class InvalidItineraryRequestException extends IllegalArgumentException {
    public InvalidItineraryRequestException(String message) {
        super(message);
    }
}