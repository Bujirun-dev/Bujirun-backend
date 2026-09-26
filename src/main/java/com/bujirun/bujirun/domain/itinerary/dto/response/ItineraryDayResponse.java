package com.bujirun.bujirun.domain.itinerary.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ItineraryDayResponse(
        UUID id,
        int dayNumber,
        LocalDate date,
        // 낙관적 락 버전. 프론트가 이 값을 보관해뒀다가 다음 replaceDayItems/reorderItems
        // 요청의 expectedVersion으로 실어 보낸다. Long으로 두는 이유: @Version 필드는 실제로
        // 저장(persist)되기 전까지는 null이라(단위 테스트에서 builder()로만 만든 미저장
        // 엔티티가 대표적) long으로 두면 그 경우 언박싱 NPE가 난다.
        Long version,
        List<ItineraryItemResponse> items
) {
    public static ItineraryDayResponse from(ItineraryDay day, Set<UUID> collectedSpotIds, Set<UUID> visitedItemIds) {
        return new ItineraryDayResponse(
                day.getId(),
                day.getDayNumber(),
                day.getDate(),
                day.getVersion(),
                day.getItems().stream().map(i -> ItineraryItemResponse.from(i, collectedSpotIds, visitedItemIds)).toList()
        );
    }
}
