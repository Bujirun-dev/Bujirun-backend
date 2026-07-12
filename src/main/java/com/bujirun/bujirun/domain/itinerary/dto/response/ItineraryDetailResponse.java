package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ItineraryDetailResponse(
        UUID id,
        UUID userId,
        UUID sessionId,
        UUID groupId,
        String title,
        String planType,
        String status,
        LocalDate startAt,
        LocalDate endAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ItineraryDayResponse> days
) {
    public static ItineraryDetailResponse from(Itinerary itinerary, Set<UUID> collectedSpotIds, Set<UUID> visitedSpotIds) {
        return new ItineraryDetailResponse(
                itinerary.getId(),
                itinerary.getUserId(),
                itinerary.getSessionId(),
                itinerary.getGroupId(),
                itinerary.getTitle(),
                itinerary.getPlanType(),
                itinerary.getStatus(),
                itinerary.getStartAt(),
                itinerary.getEndAt(),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt(),
                itinerary.getDays().stream().map(d -> ItineraryDayResponse.from(d, collectedSpotIds, visitedSpotIds)).toList()
        );
    }
}
