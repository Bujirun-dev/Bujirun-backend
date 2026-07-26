package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.log.entity.TravelLogItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TravelLogDayResponse(
        int dayNumber,
        LocalDate date,
        List<TravelLogItemResponse> items
) {
    public static TravelLogDayResponse of(ItineraryDay day, Map<UUID, TravelLogItem> logItemMap, Set<UUID> visitedItemIds) {
        List<TravelLogItemResponse> itemResponses = day.getItems().stream()
                .filter(i -> logItemMap.containsKey(i.getId()))
                .map(i -> {
                    TravelLogItem logItem = logItemMap.get(i.getId());
                    return TravelLogItemResponse.of(logItem, i, visitedItemIds.contains(i.getId()));
                })
                .toList();

        return new TravelLogDayResponse(day.getDayNumber(), day.getDate(), itemResponses);
    }
}
