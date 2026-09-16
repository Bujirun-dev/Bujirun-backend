package com.bujirun.bujirun.domain.spot.dto.response;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;

import java.util.UUID;

public record SpotSearchResponse(
        UUID spotId,
        String contentId,
        String name,
        String category,
        String collectionCategory, // (바다/자연/문화/체험) — 도감 여부와 무관하게 전체 관광지에 채워짐(spotCategory 승계)
        Integer sigunguId,
        String sigunguName,
        String address,
        String thumbnailUrl,
        String swipeImageUrl,
        boolean isCollection,
        boolean collected,
        boolean visited
) {
    public static SpotSearchResponse from(TourSpot spot, boolean collected, boolean visited) {
        return new SpotSearchResponse(
                spot.getId(),
                spot.getContentId(),
                spot.getName(),
                spot.getCategory(),
                spot.getSpotCategory(),
                spot.getSigungu() != null ? spot.getSigungu().getId() : null,
                spot.getSigungu() != null ? spot.getSigungu().getName() : null,
                spot.getAddress(),
                spot.getThumbnailUrl(),
                spot.getSwipeImageUrl(),
                spot.isCollection(),
                collected,
                visited
        );
    }
}