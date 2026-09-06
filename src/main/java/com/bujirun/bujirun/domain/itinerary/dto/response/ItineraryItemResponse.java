package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitDetail;
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
        String routeType,
        String routeNo,
        String startStationName,
        String endStationName,
        String startArsId,
        TransitDetail transitDetail, // subPath 배열 전체 (환승 2회 이상 등 대표값으로 못 담는 구간 상세). "예정" 정보, 실시간 아님
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
            boolean collected,
            boolean visited
    ) {}

    // collectedSpotIds: 도감 수집 여부(관광지 단위, 전역) / visitedItemIds: 이 일정의 이 방문 항목을
    // 인증했는지(항목 단위) — 같은 관광지라도 일정마다 따로 인증해야 하므로 spotId가 아닌 itemId로 판단한다.
    public static ItineraryItemResponse from(ItineraryItem item, Set<UUID> collectedSpotIds, Set<UUID> visitedItemIds) {
        TourSpot s = item.getSpot();
        return new ItineraryItemResponse(
                item.getId(),
                item.getOrderIndex(),
                new SpotSummary(
                        s.getId(), s.getName(), s.getCategory(),
                        s.getAddress(), s.getLat(), s.getLng(),
                        s.getThumbnailUrl(),
                        collectedSpotIds.contains(s.getId()),
                        visitedItemIds.contains(item.getId())
                ),
                item.getArrivalTime(),
                item.getDurationMin(),
                item.getTravelMode(),
                item.getTravelTimeMin(),
                item.getRouteType(),
                item.getRouteNo(),
                item.getStartStationName(),
                item.getEndStationName(),
                item.getStartArsId(),
                item.getTransitDetail(),
                item.getMemo()
        );
    }
}
