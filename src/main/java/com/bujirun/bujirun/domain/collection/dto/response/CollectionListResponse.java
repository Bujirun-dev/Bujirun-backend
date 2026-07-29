package com.bujirun.bujirun.domain.collection.dto.response;

import com.bujirun.bujirun.domain.collection.repository.CollectionListProjection;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollectionListResponse(
        UUID spotId,
        String name,
        Integer sigunguId,
        String thumbnailUrl,
        String collectionCategory,
        boolean collected,
        LocalDateTime collectedAt
) {
    public static CollectionListResponse from(CollectionListProjection p) {
        return new CollectionListResponse(
                p.getSpotId(), p.getName(), p.getSigunguId(),
                p.getThumbnailUrl(),
                p.getCollectionCategory(),
                Boolean.TRUE.equals(p.getCollected()), // null이면 false로 처리
                p.getCollectedAt()
        );
    }
}