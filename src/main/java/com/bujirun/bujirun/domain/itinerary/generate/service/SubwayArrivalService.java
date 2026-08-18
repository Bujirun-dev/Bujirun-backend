package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.OdsayClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDaySchedule;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDeparture;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwaySchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * "다음 지하철까지 몇 분 남았는지"를 ODsay 배차 시각표(searchSubwaySchedule) 기준으로 계산해서 제공한다.
 * 부산 지하철은 ODsay에서도 GPS 기반 실시간 도착정보를 제공하지 않으므로, 이 값은 시각표상 다음 열차
 * 출발 예정시각과 현재시각의 차이일 뿐 실시간 위치 추적 결과가 아니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubwayArrivalService implements ArrivalInfoProvider {

    private final OdsayClient odsayClient;

    @Override
    public boolean supports(String type) {
        return "지하철".equals(type);
    }

    @Override
    public Integer getNextArrival(SubPath subPath) {
        if (subPath.startId() == 0) {
            log.info("지하철 역코드 없음 — {}", subPath.startName());
            return null;
        }
        try {
            SubwaySchedule schedule = odsayClient.searchSubwaySchedule(subPath.startId(), subPath.wayCode());
            return parseNextArrival(schedule, subPath.wayCode());
        } catch (Exception e) {
            log.warn("지하철 시각표 조회 실패 stationId={}: {}", subPath.startId(), e.getMessage());
            return null;
        }
    }

    private Integer parseNextArrival(SubwaySchedule schedule, int wayCode) {
        if (schedule == null) return null;

        SubwayDaySchedule daySchedule = resolveSchedule(schedule);
        if (daySchedule == null) return null;

        List<SubwayDeparture> departures = wayCode == 1 ? daySchedule.up() : daySchedule.down();
        if (departures == null || departures.isEmpty()) return null;

        LocalTime now = LocalTime.now();

        for (SubwayDeparture departure : departures) {
            String departureTime = departure.departureTime();
            if (departureTime == null || departureTime.isBlank()) continue;

            // departureTime 형식: "HH:mm" 또는 "H:mm"
            try {
                String[] parts = departureTime.split(":");
                int hour = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);
                if (hour >= 24) hour -= 24;

                LocalTime trainTime = LocalTime.of(hour, min);
                int diff = (int) Duration.between(now, trainTime).toMinutes();
                if (diff >= 0) {
                    log.info("다음 지하철 → {}분 후 ({}:{})", diff, hour, min);
                    return diff;
                }
            } catch (Exception e) {
                log.warn("시각 파싱 실패: {}", departureTime);
            }
        }

        log.warn("오늘 남은 열차 없음");
        return null;
    }

    private SubwayDaySchedule resolveSchedule(SubwaySchedule schedule) {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) return schedule.saturdaySchedule();
        if (day == DayOfWeek.SUNDAY) return schedule.holidaySchedule();
        return schedule.weekdaySchedule();
    }
}
