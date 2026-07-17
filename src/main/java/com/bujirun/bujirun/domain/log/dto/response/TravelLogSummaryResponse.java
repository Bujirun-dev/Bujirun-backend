package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.log.entity.TravelLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelLogSummaryResponse(
        UUID id,
        String title,
        String thumbnailPhotoUrl,
        boolean isPublic,
        LocalDate startDate,
        int totalSpots,
        int collectedSpots,
        String authorNickname,
        int addedCount,
        Integer mood,
        String theme,
        LocalDateTime createdAt
) {
    public static TravelLogSummaryResponse of(TravelLog log, Itinerary itinerary, String authorNickname, int collectedSpots) {
        int totalSpots = itinerary.getDays().stream()
                .mapToInt(d -> d.getItems().size())
                .sum();

        LocalDate startDate = itinerary.getStartAt() != null ? itinerary.getStartAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(0).getDate());

        return new TravelLogSummaryResponse(
                log.getId(),
                itinerary.getTitle(),
                log.getThumbnailPhotoUrl(),
                log.isPublic(),
                startDate,
                totalSpots,
                collectedSpots,
                authorNickname,
                log.getAddedCount(),
                log.getMood(),
                log.getTheme(),
                log.getCreatedAt()
        );
    }
}
