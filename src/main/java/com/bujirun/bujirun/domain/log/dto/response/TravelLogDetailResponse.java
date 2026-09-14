package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.group.dto.response.GroupMemberResponse;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.log.entity.TravelLog;
import com.bujirun.bujirun.domain.log.entity.TravelLogItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TravelLogDetailResponse(
        UUID id,
        UUID itineraryId,
        String title,
        int totalSpots,
        int collectedSpots,
        String duration,
        LocalDate startDate,
        LocalDate endDate,
        boolean isPublic,
        String thumbnailPhotoUrl,
        Integer mood,
        String theme,
        int travelNumber,
        LocalDateTime createdAt,
        List<TravelLogDayResponse> days,
        List<GroupMemberResponse> groupMembers
) {
    public static TravelLogDetailResponse of(TravelLog log, Itinerary itinerary, Map<UUID, TravelLogItem> logItemMap,
                                              Set<UUID> visitedItemIds, List<GroupMemberResponse> groupMembers, int collectedSpots) {
        int totalSpots = itinerary.getDays().stream()
                .mapToInt(d -> d.getItems().size())
                .sum();

        // itinerary_days 행 수를 우선 쓴다 — 바로 아래 days(응답의 days[] 배열)도 같은 값으로
        // 만들어지므로, 이 응답 안에서 "N일치 일정이라면서 duration은 당일치기" 같은 자기모순이
        // 생기지 않는다. startAt/endAt은 편집 이력에 따라 실제 day 행 수와 어긋날 수 있어서
        // (예: 날짜만 줄이고 day 행 정리가 안 된 경우) days가 비어있을 때만 날짜로 계산한다.
        int dayCount = !itinerary.getDays().isEmpty() ? itinerary.getDays().size()
                : (itinerary.getStartAt() != null && itinerary.getEndAt() != null)
                        ? (int) ChronoUnit.DAYS.between(itinerary.getStartAt(), itinerary.getEndAt()) + 1
                        : 1;
        String duration = dayCount <= 1 ? "당일치기" : (dayCount - 1) + "박 " + dayCount + "일";

        LocalDate startDate = itinerary.getStartAt() != null ? itinerary.getStartAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(0).getDate());
        LocalDate endDate = itinerary.getEndAt() != null ? itinerary.getEndAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(itinerary.getDays().size() - 1).getDate());

        List<TravelLogDayResponse> days = itinerary.getDays().stream()
                .map(d -> TravelLogDayResponse.of(d, logItemMap, visitedItemIds))
                .toList();

        return new TravelLogDetailResponse(
                log.getId(),
                log.getItineraryId(),
                itinerary.getTitle(),
                totalSpots,
                collectedSpots,
                duration,
                startDate,
                endDate,
                log.isPublic(),
                log.getThumbnailPhotoUrl(),
                log.getMood(),
                log.getTheme(),
                log.getTravelNumber(),
                log.getCreatedAt(),
                days,
                groupMembers
        );
    }
}
