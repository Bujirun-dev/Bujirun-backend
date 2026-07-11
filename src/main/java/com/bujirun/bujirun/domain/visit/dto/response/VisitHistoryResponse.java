package com.bujirun.bujirun.domain.visit.dto.response;

import com.bujirun.bujirun.domain.visit.entity.Visit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record VisitHistoryResponse(
        UUID visitId,
        UUID spotId,
        String spotName,
        String spotThumbnailUrl,
        boolean verified,
        double distanceMeters,
        LocalDateTime visitedAt,
        List<String> photoUrls
) {
    public static VisitHistoryResponse from(Visit visit, List<String> photoUrls) {
        return new VisitHistoryResponse(
                visit.getId(),
                visit.getSpot().getId(),
                visit.getSpot().getName(),
                visit.getSpot().getThumbnailUrl(),
                visit.isVerified(),
                visit.getDistanceMeters(),
                visit.getVisitedAt(),
                photoUrls
        );
    }
}
