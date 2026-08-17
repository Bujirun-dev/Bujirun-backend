package com.bujirun.bujirun.domain.collection.dto.response;

import java.util.List;

public record MyCollectionResponse(
        long totalCount,
        long collectedCount,
        List<CollectionListResponse> entries,
        List<CollectionListResponse> uncollectedEntries
) {
    public static MyCollectionResponse of(
            long totalCount,
            long collectedCount,
            List<CollectionListResponse> entries,
            List<CollectionListResponse> uncollectedEntries
    ) {
        return new MyCollectionResponse(totalCount, collectedCount, entries, uncollectedEntries);
    }
}