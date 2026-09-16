package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.log.entity.TravelLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record TravelLogSummaryResponse(
        UUID id,
        UUID itineraryId,
        String title,
        String firstSpotName,
        String thumbnailPhotoUrl,
        boolean isPublic,
        String duration,
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

        // 목록 카드의 "관광지명 외 N곳" 표기는 일정 제목(title)이 아니라 첫 방문 관광지명을 써야 하므로 별도로 계산
        String firstSpotName = itinerary.getDays().stream()
                .flatMap(d -> d.getItems().stream())
                .findFirst()
                .map(item -> item.getSpot().getName())
                .orElse(null);

        LocalDate startDate = itinerary.getStartAt() != null ? itinerary.getStartAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(0).getDate());
        LocalDate endDate = itinerary.getEndAt() != null ? itinerary.getEndAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(itinerary.getDays().size() - 1).getDate());

        // TravelLogDetailResponse와 동일한 계산 방식 — days 행 수를 우선 쓰고, 비어있을 때만 날짜로 계산
        int dayCount = !itinerary.getDays().isEmpty() ? itinerary.getDays().size()
                : (itinerary.getStartAt() != null && itinerary.getEndAt() != null)
                        ? (int) ChronoUnit.DAYS.between(itinerary.getStartAt(), itinerary.getEndAt()) + 1
                        : 1;
        String duration = dayCount <= 1 ? "당일치기" : (dayCount - 1) + "박 " + dayCount + "일";

        return new TravelLogSummaryResponse(
                log.getId(),
                log.getItineraryId(),
                itinerary.getTitle(),
                firstSpotName,
                thumbnailPhotoUrl,
                log.isPublic(),
                duration,
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
