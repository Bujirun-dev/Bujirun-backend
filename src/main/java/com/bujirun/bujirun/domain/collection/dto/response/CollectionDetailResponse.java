package com.bujirun.bujirun.domain.collection.dto.response;

import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollectionDetailResponse(
        UUID spotId,
        boolean collected,
        LocalDateTime collectedAt
) {
    public static CollectionDetailResponse from(CollectionEntry entry) {
        return new CollectionDetailResponse(
                entry.getSpot().getId(), entry.isCollected(), entry.getCollectedAt()
        );
    }
}