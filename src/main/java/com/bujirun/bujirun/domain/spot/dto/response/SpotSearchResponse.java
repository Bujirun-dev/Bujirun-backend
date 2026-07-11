package com.bujirun.bujirun.domain.spot.dto.response;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;

import java.util.UUID;

public record SpotSearchResponse(
        UUID spotId,
        String contentId,
        String name,
        String category,
        Integer sigunguId,
        String sigunguName,
        String address,
        String thumbnailUrl,
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
                spot.getSigungu() != null ? spot.getSigungu().getId() : null,
                spot.getSigungu() != null ? spot.getSigungu().getName() : null,
                spot.getAddress(),
                spot.getThumbnailUrl(),
                spot.isCollection(),
                collected,
                visited
        );
    }
}