package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ItineraryDayResponse(
        UUID id,
        int dayNumber,
        LocalDate date,
        List<ItineraryItemResponse> items
) {
    public static ItineraryDayResponse from(ItineraryDay day, Set<UUID> collectedSpotIds, Set<UUID> visitedSpotIds) {
        return new ItineraryDayResponse(
                day.getId(),
                day.getDayNumber(),
                day.getDate(),
                day.getItems().stream().map(i -> ItineraryItemResponse.from(i, collectedSpotIds, visitedSpotIds)).toList()
        );
    }
}
