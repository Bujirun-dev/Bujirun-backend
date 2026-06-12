package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import java.util.List;

public record TransitOption(
        String type,
        int totalTime,
        int totalFare,
        int transferCount,
        boolean estimated,
        List<SubPath> subPaths
) {}