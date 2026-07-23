package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.log.entity.TravelLogItem;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TravelLogItemResponse(
        UUID id,
        UUID itineraryItemId,
        UUID spotId,
        String spotName,
        String spotCategory,
        String spotAddress,
        BigDecimal spotLat,
        BigDecimal spotLng,
        String spotThumbnailUrl,
        LocalTime arrivalTime,
        int orderIndex,
        List<TravelLogPhotoResponse> photos,
        List<TravelLogHashtagResponse> hashtags
) {
    public static TravelLogItemResponse of(TravelLogItem logItem, ItineraryItem itineraryItem) {
        TourSpot spot = itineraryItem.getSpot();
        return new TravelLogItemResponse(
                logItem.getId(),
                itineraryItem.getId(),
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getAddress(),
                spot.getLat(),
                spot.getLng(),
                spot.getThumbnailUrl(),
                itineraryItem.getArrivalTime(),
                itineraryItem.getOrderIndex(),
                logItem.getPhotos().stream().map(TravelLogPhotoResponse::from).toList(),
                logItem.getHashtags().stream().map(TravelLogHashtagResponse::from).toList()
        );
    }
}
