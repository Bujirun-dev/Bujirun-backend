package com.bujirun.bujirun.global.util;

import java.time.Duration;
import java.time.LocalTime;

public final class ScheduleCapacityUtil {

    private static final int HOURS_PER_SPOT = 2;
    private static final int MAX_SPOTS_PER_DAY = 4;
    private static final int MIN_SPOTS_PER_DAY = 1;

    private ScheduleCapacityUtil() {}

    /**
     * 활동 시간(시간 단위)을 기준으로 하루 최대 방문 관광지 수를 계산
     * 기준: 2시간당 1곳, 최대 4곳, 최소 1곳
     */
    public static int calculateMaxSpotsPerDay(int activityHours) {
        int count = activityHours / HOURS_PER_SPOT;
        return Math.max(MIN_SPOTS_PER_DAY, Math.min(MAX_SPOTS_PER_DAY, count));
    }

    /**
     * 일차별 실제 활동 가능 시간(시간 단위)을 계산.
     * - 총 1일(당일치기): 시작시각~종료시각
     * - 첫째 날(dayNumber=1): 시작시각~자정
     * - 마지막 날(dayNumber=totalDays): 자정~종료시각
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
            return toHours(tripStartTime, LocalTime.MAX); // 시작시각 ~ 23:59:59
        }

        if (dayNumber == totalDays) {
            return toHours(LocalTime.MIDNIGHT, tripEndTime); // 00:00 ~ 종료시각
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
