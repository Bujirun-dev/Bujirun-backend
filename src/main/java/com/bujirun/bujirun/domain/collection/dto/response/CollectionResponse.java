package com.bujirun.bujirun.domain.collection.dto.response;

import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollectionResponse(
        UUID spotId,
        String contentId,
        String name,
        String category,
        String collectionCategory,
        String thumbnailUrl,
        LocalDateTime collectedAt
) {
    public static CollectionResponse of(CollectionEntry entry) {
        var spot = entry.getSpot();
        return new CollectionResponse(
                spot.getId(),
                spot.getContentId(),
                spot.getName(),
                spot.getCategory(),
                spot.getCollectionCategory(),
                spot.getThumbnailUrl(),
                entry.getCollectedAt()
        );
    }
}
