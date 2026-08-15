package com.bujirun.bujirun.global.util;

import java.time.Duration;
import java.time.LocalTime;

public final class ScheduleCapacityUtil {

    private static final int HOURS_PER_SPOT = 2;
    private static final int MAX_SPOTS_PER_DAY = 3;
    private static final int MIN_SPOTS_PER_DAY = 1;

    // 하루 활동 가능 시간대 (첫날/마지막날 계산 기준)
    private static final LocalTime DAY_ACTIVITY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_ACTIVITY_END = LocalTime.of(21, 0);

    private ScheduleCapacityUtil() {}

    /**
     * 활동 시간(시간 단위)을 기준으로 하루 최대 방문 관광지 수를 계산
     * 기준: 2시간당 1곳, 최대 3곳, 최소 1곳
     */
    public static int calculateMaxSpotsPerDay(int activityHours) {
        int count = activityHours / HOURS_PER_SPOT;
        return Math.max(MIN_SPOTS_PER_DAY, Math.min(MAX_SPOTS_PER_DAY, count));
    }

    /**
     * 일차별 실제 활동 가능 시간(시간 단위)을 계산.
     * - 총 1일(당일치기): 시작시각~종료시각
     * - 첫째 날(dayNumber=1): 시작시각~활동 종료 기준시각(21:00). 시작시각이 21:00 이후면 0시간
     * - 마지막 날(dayNumber=totalDays): 활동 시작 기준시각(09:00)~종료시각. 종료시각이 09:00 이전이면 0시간
     * - 중간 날: defaultActivityHours 그대로 사용
     *
     * tripStartTime/tripEndTime이 null이면(시간 미입력) 모든 날에 defaultActivityHours 적용
     */

    public static int calculateActivityHoursForDay(int dayNumber, int totalDays,
                                                   LocalTime tripStartTime, LocalTime tripEndTime,
                                                   int defaultActivityHours) {
        if (tripStartTime == null || tripEndTime == null) {
            return defaultActivityHours;
        }

        if (totalDays == 1) {
            return toHours(tripStartTime, tripEndTime);
        }

        if (dayNumber == 1) {
            // 09:00~21:00 활동 종료 기준
            LocalTime effectiveEnd = tripStartTime.isAfter(DAY_ACTIVITY_END) ? tripStartTime : DAY_ACTIVITY_END;
            return toHours(tripStartTime, effectiveEnd);
        }

        if (dayNumber == totalDays) {
            // 09:00~21:00 활동 종료 기준
            LocalTime effectiveStart = tripEndTime.isBefore(DAY_ACTIVITY_START) ? tripEndTime : DAY_ACTIVITY_START;
            return toHours(effectiveStart, tripEndTime);
        }

        return defaultActivityHours;
    }

    /**
     * 일차별 최대 관광지 수를 한 번에 계산 (위 두 메서드 조합)
     */
    public static int calculateMaxSpotsForDay(int dayNumber, int totalDays,
                                              LocalTime tripStartTime, LocalTime tripEndTime,
                                              int defaultActivityHours) {
        int hours = calculateActivityHoursForDay(dayNumber, totalDays, tripStartTime, tripEndTime, defaultActivityHours);
        return calculateMaxSpotsPerDay(hours);
    }

    private static int toHours(LocalTime start, LocalTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return (int) Math.max(0, minutes / 60);
    }
}
