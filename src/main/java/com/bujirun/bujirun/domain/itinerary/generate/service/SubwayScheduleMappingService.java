package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.OdsayClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDaySchedule;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDeparture;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwaySchedule;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwaySegmentTimetable;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayTransitInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.global.util.TransitRouteUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 대중교통 길찾기 결과(searchPubTransPathT)의 subPath 중 지하철(trafficType=1) 구간에
 * ODsay 배차 시각표(searchSubwaySchedule) / 환승정보(subwayTransitInfo)를 매핑한다.
 * <p>
 * 환승이 2회 이상이어서 지하철 구간이 여러 개인 경로도 전 구간을 순회해서 매핑한다.
 * 결과는 전부 "예정"(시각표 기준) 정보이며 GPS 기반 실시간 도착정보가 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubwayScheduleMappingService {

    private static final int UPCOMING_DEPARTURE_LIMIT = 3;

    private final OdsayClient odsayClient;

    public List<SubwaySegmentTimetable> mapSubwaySegments(TransitOption option) {
        if (option == null || option.subPaths() == null) return List.of();

        List<SubPath> subPaths = option.subPaths();
        List<SubwaySegmentTimetable> result = new ArrayList<>();

        for (Map.Entry<Integer, SubPath> entry : TransitRouteUtils.findSubwaySubPaths(subPaths)) {
            result.add(mapSegment(subPaths, entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private SubwaySegmentTimetable mapSegment(List<SubPath> subPaths, int index, SubPath subPath) {
        if (subPath.startId() == 0) {
            log.info("지하철 역코드 없음 — {} 구간(index={}) 시각표 매핑 스킵", subPath.startName(), index);
            return new SubwaySegmentTimetable(index, subPath.startName(), subPath.endName(),
                    subPath.routeNo(), subPath.wayCode(), List.of(), null, null);
        }

        List<SubwayDeparture> upcoming;
        try {
            SubwaySchedule schedule = odsayClient.searchSubwaySchedule(subPath.startId(), subPath.wayCode());
            upcoming = extractUpcomingDepartures(schedule, subPath.wayCode());
        } catch (Exception e) {
            log.warn("지하철 시각표 매핑 실패 stationId={}: {}", subPath.startId(), e.getMessage());
            upcoming = List.of();
        }

        Integer nextMinutes = upcoming.isEmpty() ? null : minutesUntil(upcoming.get(0).departureTime());

        // 직전 구간도 지하철이면(=환승 후 재승차 구간) 이 역의 환승정보를 함께 채운다.
        boolean isTransferBoarding = index > 0 && "지하철".equals(subPaths.get(index - 1).type());
        SubwayTransitInfo transferInfo = isTransferBoarding ? fetchTransferInfo(subPath.startId()) : null;

        return new SubwaySegmentTimetable(index, subPath.startName(), subPath.endName(),
                subPath.routeNo(), subPath.wayCode(), upcoming, nextMinutes, transferInfo);
    }

    private SubwayTransitInfo fetchTransferInfo(int stationId) {
        try {
            return odsayClient.getSubwayTransitInfo(stationId);
        } catch (Exception e) {
            log.warn("지하철 환승정보 조회 실패 stationId={}: {}", stationId, e.getMessage());
            return null;
        }
    }

    private List<SubwayDeparture> extractUpcomingDepartures(SubwaySchedule schedule, int wayCode) {
        if (schedule == null) return List.of();

        SubwayDaySchedule daySchedule = resolveDaySchedule(schedule);
        if (daySchedule == null) return List.of();

        List<SubwayDeparture> direction = wayCode == 1 ? daySchedule.up() : daySchedule.down();
        if (direction == null || direction.isEmpty()) return List.of();

        LocalTime now = LocalTime.now();
        List<SubwayDeparture> upcoming = new ArrayList<>();
        for (SubwayDeparture departure : direction) {
            LocalTime time = parseDepartureTime(departure.departureTime());
            if (time == null || time.isBefore(now)) continue;
            upcoming.add(departure);
            if (upcoming.size() >= UPCOMING_DEPARTURE_LIMIT) break;
        }
        return upcoming;
    }

    private SubwayDaySchedule resolveDaySchedule(SubwaySchedule schedule) {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) return schedule.saturdaySchedule();
        if (day == DayOfWeek.SUNDAY) return schedule.holidaySchedule();
        return schedule.weekdaySchedule();
    }

    private Integer minutesUntil(String departureTime) {
        LocalTime time = parseDepartureTime(departureTime);
        if (time == null) return null;
        return (int) Duration.between(LocalTime.now(), time).toMinutes();
    }

    private LocalTime parseDepartureTime(String departureTime) {
        if (departureTime == null || departureTime.isBlank()) return null;
        try {
            String[] parts = departureTime.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());
            if (hour >= 24) hour -= 24;
            return LocalTime.of(hour, min);
        } catch (Exception e) {
            log.warn("지하철 시각 파싱 실패: {}", departureTime);
            return null;
        }
    }
}
