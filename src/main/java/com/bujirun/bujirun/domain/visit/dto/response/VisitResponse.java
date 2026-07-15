package com.bujirun.bujirun.domain.visit.dto.response;

import com.bujirun.bujirun.domain.visit.entity.Visit;

import java.util.UUID;

public record VisitResponse(
        UUID visitId,
        boolean verified,
        double distanceMeters,
        boolean firstVisit
) {
    public static VisitResponse from(Visit visit, boolean firstVisit) {
        return new VisitResponse(visit.getId(), visit.isVerified(), visit.getDistanceMeters(), firstVisit);
    }
}
