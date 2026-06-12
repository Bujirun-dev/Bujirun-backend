package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record SubPath(
        String type,
        int sectionTime,
        String routeNo,
        int stationCount
) {}