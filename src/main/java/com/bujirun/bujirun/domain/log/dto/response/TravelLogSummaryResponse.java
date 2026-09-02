package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.log.entity.TravelLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelLogSummaryResponse(
        UUID id,
        UUID itineraryId,
        String title,
        String thumbnailPhotoUrl,
        boolean isPublic,
        LocalDate startDate,
        LocalDate endDate,
        int totalSpots,
        int collectedSpots,
        String authorNickname,
        int addedCount,
        Integer mood,
        String theme,
        int travelNumber,
        LocalDateTime createdAt
) {
    public static TravelLogSummaryResponse of(TravelLog log, Itinerary itinerary, String authorNickname, int collectedSpots) {
        return of(log, itinerary, authorNickname, collectedSpots, log.getThumbnailPhotoUrl());
    }

    // thumbnailPhotoUrl을 호출부에서 지정하는 버전 — 관광지 둘러보기 화면처럼 문맥에 따라
    // 로그의 대표 사진이 아닌 다른 사진(그 관광지 사진 등)을 썸네일로 써야 할 때 사용한다.
    public static TravelLogSummaryResponse of(TravelLog log, Itinerary itinerary, String authorNickname,
                                              int collectedSpots, String thumbnailPhotoUrl) {
        int totalSpots = itinerary.getDays().stream()
                .mapToInt(d -> d.getItems().size())
                .sum();

        LocalDate startDate = itinerary.getStartAt() != null ? itinerary.getStartAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(0).getDate());
        LocalDate endDate = itinerary.getEndAt() != null ? itinerary.getEndAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(itinerary.getDays().size() - 1).getDate());

        return new TravelLogSummaryResponse(
                log.getId(),
                log.getItineraryId(),
                itinerary.getTitle(),
                thumbnailPhotoUrl,
                log.isPublic(),
                startDate,
                endDate,
                totalSpots,
                collectedSpots,
                authorNickname,
                log.getAddedCount(),
                log.getMood(),
                log.getTheme(),
                log.getTravelNumber(),
                log.getCreatedAt()
        );
    }
}
