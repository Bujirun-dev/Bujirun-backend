package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record ItinerarySummaryResponse(
        UUID id,
        UUID groupId,
        String title,
        String planType,
        String status,
        LocalDate startAt,
        LocalTime startTime,
        LocalDate endAt,
        LocalTime endTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItinerarySummaryResponse from(Itinerary itinerary) {
        return new ItinerarySummaryResponse(
                itinerary.getId(),
                itinerary.getGroupId(),
                itinerary.getTitle(),
                itinerary.getPlanType(),
                itinerary.getStatus(),
                itinerary.getStartAt(),
                itinerary.getStartTime(),
                itinerary.getEndAt(),
                itinerary.getEndTime(),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt()
        );
    }
}
