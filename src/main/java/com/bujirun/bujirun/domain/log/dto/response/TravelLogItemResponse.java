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
        String spotCollectionCategory, // (바다/자연/문화/체험) — 도감 여부와 무관하게 전체 관광지에 채워짐(spotCategory 승계)
        String spotAddress,
        BigDecimal spotLat,
        BigDecimal spotLng,
        String spotThumbnailUrl,
        LocalTime arrivalTime,
        int orderIndex,
        boolean visited,
        List<TravelLogPhotoResponse> photos,
        List<TravelLogHashtagResponse> hashtags
) {
    public static TravelLogItemResponse of(TravelLogItem logItem, ItineraryItem itineraryItem, boolean visited) {
        TourSpot spot = itineraryItem.getSpot();
        return new TravelLogItemResponse(
                logItem.getId(),
                itineraryItem.getId(),
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getSpotCategory(),
                spot.getAddress(),
                spot.getLat(),
                spot.getLng(),
                spot.getThumbnailUrl(),
                itineraryItem.getArrivalTime(),
                itineraryItem.getOrderIndex(),
                visited,
                logItem.getPhotos().stream().map(TravelLogPhotoResponse::from).toList(),
                logItem.getHashtags().stream().map(TravelLogHashtagResponse::from).toList()
        );
    }
}
