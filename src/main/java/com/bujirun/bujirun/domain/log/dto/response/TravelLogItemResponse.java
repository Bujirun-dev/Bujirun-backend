package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.log.entity.TravelLogItem;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TravelLogItemResponse(
        UUID id,
        UUID itineraryItemId,
        String spotName,
        String spotCategory,
        LocalTime arrivalTime,
        int orderIndex,
        List<TravelLogPhotoResponse> photos,
        List<TravelLogHashtagResponse> hashtags
) {
    public static TravelLogItemResponse of(TravelLogItem logItem, ItineraryItem itineraryItem) {
        return new TravelLogItemResponse(
                logItem.getId(),
                itineraryItem.getId(),
                itineraryItem.getSpot().getName(),
                itineraryItem.getSpot().getCategory(),
                itineraryItem.getArrivalTime(),
                itineraryItem.getOrderIndex(),
                logItem.getPhotos().stream().map(TravelLogPhotoResponse::from).toList(),
                logItem.getHashtags().stream().map(TravelLogHashtagResponse::from).toList()
        );
    }
}
