package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.log.entity.TravelLog;
import com.bujirun.bujirun.domain.log.entity.TravelLogItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TravelLogDetailResponse(
        UUID id,
        UUID itineraryId,
        String title,
        int totalSpots,
        String duration,
        LocalDate startDate,
        boolean isPublic,
        String thumbnailPhotoUrl,
        LocalDateTime createdAt,
        List<TravelLogDayResponse> days
) {
    public static TravelLogDetailResponse of(TravelLog log, Itinerary itinerary, Map<UUID, TravelLogItem> logItemMap) {
        int totalSpots = itinerary.getDays().stream()
                .mapToInt(d -> d.getItems().size())
                .sum();

        String duration;
        if (itinerary.getStartAt() != null && itinerary.getEndAt() != null) {
            int days = (int) ChronoUnit.DAYS.between(itinerary.getStartAt(), itinerary.getEndAt()) + 1;
            duration = days <= 1 ? "당일치기" : (days - 1) + "박 " + days + "일";
        } else {
            int dayCount = itinerary.getDays().size();
            duration = dayCount <= 1 ? "당일치기" : (dayCount - 1) + "박 " + dayCount + "일";
        }

        LocalDate startDate = itinerary.getStartAt() != null ? itinerary.getStartAt()
                : (itinerary.getDays().isEmpty() ? null : itinerary.getDays().get(0).getDate());

        List<TravelLogDayResponse> days = itinerary.getDays().stream()
                .map(d -> TravelLogDayResponse.of(d, logItemMap))
                .toList();

        return new TravelLogDetailResponse(
                log.getId(),
                log.getItineraryId(),
                itinerary.getTitle(),
                totalSpots,
                duration,
                startDate,
                log.isPublic(),
                log.getThumbnailPhotoUrl(),
                log.getCreatedAt(),
                days
        );
    }
}
