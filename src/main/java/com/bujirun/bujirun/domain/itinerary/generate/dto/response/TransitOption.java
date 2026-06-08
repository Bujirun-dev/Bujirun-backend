package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record TransitOption(
        String type,
        int totalTime,
        int totalFare,
        String routeNo,
        int transferCount,
        boolean estimated
) {}