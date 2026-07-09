package com.bujirun.bujirun.global.util;

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
}
