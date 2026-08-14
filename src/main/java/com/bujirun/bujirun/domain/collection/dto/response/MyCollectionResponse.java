package com.bujirun.bujirun.domain.collection.dto.response;

import java.util.List;

public record MyCollectionResponse(
        long totalCount,
        long collectedCount,
        List<CollectionResponse> entries
) {
    public static MyCollectionResponse of(long totalCount, long collectedCount, List<CollectionResponse> entries) {
        return new MyCollectionResponse(totalCount, collectedCount, entries);
    }
}
