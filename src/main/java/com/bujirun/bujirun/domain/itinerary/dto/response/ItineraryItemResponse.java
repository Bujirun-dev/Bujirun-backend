package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record ItineraryItemResponse(
        UUID id,
        int orderIndex,
        SpotSummary spot,
        LocalTime arrivalTime,
        Integer durationMin,
        String travelMode,
        Integer travelTimeMin,
        String memo
) {
    public record SpotSummary(
            UUID id,
            String name,
            String category,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String thumbnailUrl,
            boolean isCollected,
            boolean isVisited
    ) {}

    public static ItineraryItemResponse from(ItineraryItem item, Set<UUID> collectedSpotIds, Set<UUID> visitedSpotIds) {
        TourSpot s = item.getSpot();
        return new ItineraryItemResponse(
                item.getId(),
                item.getOrderIndex(),
                new SpotSummary(
                        s.getId(), s.getName(), s.getCategory(),
                        s.getAddress(), s.getLat(), s.getLng(),
                        s.getThumbnailUrl(),
                        collectedSpotIds.contains(s.getId()),
                        visitedSpotIds.contains(s.getId())
                ),
                item.getArrivalTime(),
                item.getDurationMin(),
                item.getTravelMode(),
                item.getTravelTimeMin(),
                item.getMemo()
        );
    }
}
