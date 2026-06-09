package com.bujirun.bujirun.domain.visit.dto.response;

import com.bujirun.bujirun.domain.visit.entity.Visit;

import java.util.UUID;

public record VisitResponse(
        UUID visitId,
        boolean verified,
        double distanceMeters
) {
    public static VisitResponse from(Visit visit) {
        return new VisitResponse(visit.getId(), visit.isVerified(), visit.getDistanceMeters());
    }
}
