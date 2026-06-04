package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ItineraryDetailResponse(
        UUID id,
        UUID userId,
        String title,
        String planType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ItineraryDayResponse> days
) {
    public static ItineraryDetailResponse from(Itinerary itinerary) {
        return new ItineraryDetailResponse(
                itinerary.getId(),
                itinerary.getUserId(),
                itinerary.getTitle(),
                itinerary.getPlanType(),
                itinerary.getStatus(),
                itinerary.getCreatedAt(),
                itinerary.getUpdatedAt(),
                itinerary.getDays().stream().map(ItineraryDayResponse::from).toList()
        );
    }
}
