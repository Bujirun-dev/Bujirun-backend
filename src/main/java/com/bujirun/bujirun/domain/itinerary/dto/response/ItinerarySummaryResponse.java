package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;

import java.time.LocalDateTime;
import java.util.UUID;

public record ItinerarySummaryResponse(
        UUID id,
        String title,
        String planType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItinerarySummaryResponse from(Itinerary itinerary) {
        return new ItinerarySummaryResponse(
                itinerary.getId(),
                itinerary.getTitle(),
                itinerary.getPlanType(),
                itinerary.getStatus(),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt()
        );
    }
}
