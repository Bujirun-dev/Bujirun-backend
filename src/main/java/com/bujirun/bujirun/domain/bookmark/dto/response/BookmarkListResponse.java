package com.bujirun.bujirun.domain.bookmark.dto.response;

import com.bujirun.bujirun.domain.bookmark.entity.Bookmark;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookmarkListResponse(
        UUID spotId,
        String name,
        String category,
        Integer sigunguId,
        String thumbnailUrl,
        LocalDateTime bookmarkedAt
) {
    public static BookmarkListResponse from(Bookmark bookmark) {
        TourSpot spot = bookmark.getSpot();
        return new BookmarkListResponse(
                spot.getId(),
                spot.getName(),
                spot.getCategory(),
                spot.getSigungu() != null ? spot.getSigungu().getId() : null,
                spot.getThumbnailUrl(),
                bookmark.getBookmarkedAt()
        );
    }
}
