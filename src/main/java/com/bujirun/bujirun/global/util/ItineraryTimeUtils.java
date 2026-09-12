package com.bujirun.bujirun.global.util;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 일차(Day)별 방문 시각을 계산·보정하는 공용 유틸.
 *
 * 원래는 투표 확정(ItineraryVoteService)이 시각을 아예 채우지 않았고, 재최적화
 * (ItineraryOptimizeService)는 LocalTime.plusMinutes를 그대로 누적했으며, 여행 시작시각 변경
 * (ItineraryService)은 그 재최적화를 Day마다 다시 돌리는 식이었다. 이 계산엔 문제가 셋 있었다.
 *  ① LocalTime은 자정을 넘기면 00:20처럼 한 바퀴 돌아서, 늦은 시각 일정이 새벽 시각으로 저장된다.
 *  ② 여행 종료 시각(endTime) 상한이 어디에도 없어서 종료 시각을 훌쩍 넘긴 시각이 저장된다.
 *  ③ 같은 날 같은 시각이 만들어지면 조회 순서가 흔들리고, 프론트가 항목별로 PATCH할 때
 *     "같은 날 같은 시각" 충돌을 유발한다.
 * 그래서 시각 계산 규칙을 여기 한곳에 모았다.
 *  - 분 단위 정수로 누적하고 하루의 마지막 슬롯(23:50)을 절대 넘지 않는다.
 *  - 첫날에만 여행 시작 시각을, 마지막 날에만 여행 종료 시각을 기준으로 쓴다
 *    (프론트 clampToTripBounds와 같은 규칙 — 중간 날에는 여행 시작/종료 시각을 적용하지 않는다).
 *  - 상한에 몰려도 같은 날 항목들의 시각이 겹치지 않도록 최소 간격(10분)을 유지한다(순서는 보존).
 *  - 새로 만드는 시각은 10분 단위에 맞춘다 — 프론트가 항목 시각을 표시할 때 10분 단위로
 *    반올림(normalizeTime)하므로, 10분 단위가 아니면 화면값과 저장값이 또 갈린다.
 */
public final class ItineraryTimeUtils {

    /** 여행 시작 시각을 적용하지 않는 날(둘째 날 이후)의 기본 시작 시각 — ScheduleCapacityUtil·프론트 DEFAULT_DAY_START과 동일 */
    public static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);

    /** 하루를 넘기지 않는 마지막 슬롯 — 프론트 findFreeMinute의 LAST_MIN과 동일 */
    public static final LocalTime LAST_SLOT_OF_DAY = LocalTime.of(23, 50);

    /** 같은 날 두 항목이 같은 시각이 되지 않도록 보장하는 최소 간격 */
    public static final int MIN_GAP_MINUTES = 10;

    private static final int SLOT_MINUTES = 10;
    private static final int LAST_MINUTE_OF_DAY = 23 * 60 + 59;

    private ItineraryTimeUtils() {}

    /**
     * 그 날 일정을 시작할 기준 시각.
     * 여행 시작 시각은 "첫날 도착 시각"이라 둘째 날 이후에 그대로 쓰면 안 된다(20:00 도착인
     * 여행의 둘째 날이 20:00에 시작해버림). 첫날만 여행 시작 시각, 나머지 날은 기본값(09:00).
     */
    public static LocalTime resolveDayStartTime(int dayNumber, LocalTime tripStartTime) {
        return (dayNumber <= 1 && tripStartTime != null) ? tripStartTime : DEFAULT_DAY_START;
    }

    /**
     * 그 날 방문 시각의 상한. 여행 종료 시각은 마지막 날에만 적용하고(프론트 clampToTripBounds와 동일),
     * 그 외의 날은 자정을 넘기지 않는 것만 보장한다. totalDays를 모르면(0 이하) 마지막 날로 본다.
     */
    public static LocalTime resolveDayEndLimit(int dayNumber, int totalDays, LocalTime tripEndTime) {
        boolean lastDay = totalDays <= 0 || dayNumber >= totalDays;
        if (lastDay && tripEndTime != null && tripEndTime.isBefore(LAST_SLOT_OF_DAY)) {
            return tripEndTime;
        }
        return LAST_SLOT_OF_DAY;
    }

    /**
     * 시작 시각에서 항목 사이 간격(체류 시간 + 구간 이동 시간)을 누적해 도착 시각 목록을 만든다.
     * 결과 개수는 gapMinutes.size() + 1 (첫 항목은 시작 시각 그 자체).
     * 새로 만드는 시각이므로 10분 단위에 맞춘다.
     */
    public static List<LocalTime> accumulateArrivalTimes(LocalTime startTime, List<Integer> gapMinutes,
                                                         LocalTime dayEndLimit) {
        int gapCount = gapMinutes == null ? 0 : gapMinutes.size();
        int[] minutes = new int[gapCount + 1];
        minutes[0] = toMinuteOfDay(startTime != null ? startTime : DEFAULT_DAY_START);
        for (int i = 0; i < gapCount; i++) {
            Integer gap = gapMinutes.get(i);
            minutes[i + 1] = minutes[i] + Math.max(gap == null ? 0 : gap, MIN_GAP_MINUTES);
        }
        return normalize(minutes, dayEndLimit, true);
    }

    /**
     * 기존 방문 시각 전체를 같은 간격만큼 평행 이동한다. 순서와 항목 간 간격(사용자가 손으로
     * 정한 값일 수 있으므로 10분 단위로 다시 맞추지 않는다)을 그대로 유지하고, 자정/종료 시각
     * 상한만 지킨다. 시각이 없는(null) 항목은 그대로 null로 둔다 — 없는 값을 여기서 지어내면
     * "언제 정해진 시각인지" 구분이 사라진다.
     */
    public static List<LocalTime> shiftArrivalTimes(List<LocalTime> arrivalTimes, long shiftMinutes,
                                                    LocalTime dayEndLimit) {
        if (arrivalTimes == null || arrivalTimes.isEmpty()) return List.of();

        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < arrivalTimes.size(); i++) {
            if (arrivalTimes.get(i) != null) positions.add(i);
        }

        List<LocalTime> result = new ArrayList<>(Collections.nCopies(arrivalTimes.size(), null));
        if (positions.isEmpty()) return result;

        int[] minutes = new int[positions.size()];
        for (int k = 0; k < positions.size(); k++) {
            minutes[k] = toMinuteOfDay(arrivalTimes.get(positions.get(k))) + (int) shiftMinutes;
        }

        List<LocalTime> shifted = normalize(minutes, dayEndLimit, false);
        for (int k = 0; k < positions.size(); k++) {
            result.set(positions.get(k), shifted.get(k));
        }
        return result;
    }

    /**
     * 누적된 분 값을 (a) 하루 안에서 (b) 종료 시각 상한 아래에서 (c) 서로 겹치지 않게 보정한다.
     * 상한에 여러 항목이 몰리면 뒤에서부터 최소 간격으로 당겨서 중복을 없앤다 — 항목 순서는 유지된다.
     */
    private static List<LocalTime> normalize(int[] minutes, LocalTime dayEndLimit, boolean alignToSlot) {
        int limit = toMinuteOfDay(dayEndLimit != null ? dayEndLimit : LAST_SLOT_OF_DAY);
        if (alignToSlot) limit = (limit / SLOT_MINUTES) * SLOT_MINUTES; // 상한도 10분 단위로 내려 맞춘다
        limit = Math.min(limit, toMinuteOfDay(LAST_SLOT_OF_DAY));

        int n = minutes.length;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int value = minutes[i];
            if (alignToSlot) value = ceilToSlot(value);
            if (i > 0) value = Math.max(value, out[i - 1] + MIN_GAP_MINUTES);
            out[i] = Math.min(Math.max(value, 0), limit);
        }

        // 상한에 걸려 같은 시각이 된 뒤쪽 항목들을 앞으로 당겨 최소 간격을 되돌려준다.
        for (int i = n - 2; i >= 0; i--) {
            if (out[i] >= out[i + 1]) out[i] = out[i + 1] - MIN_GAP_MINUTES;
        }

        // 항목이 아주 많고 상한이 이른 극단적인 경우엔 앞쪽이 0분 밑으로 밀려날 수 있다.
        // 이때는 상한을 넘기더라도 00:00부터 최소 간격으로 펼친다 — 시각이 겹치거나
        // 순서가 뒤집히는 것보다는 상한을 조금 넘기는 쪽이 안전하다.
        if (n > 0 && out[0] < 0) {
            out[0] = 0;
            for (int i = 1; i < n; i++) {
                out[i] = Math.max(out[i], out[i - 1] + MIN_GAP_MINUTES);
            }
        }

        List<LocalTime> result = new ArrayList<>(n);
        for (int value : out) {
            result.add(toLocalTime(value));
        }
        return result;
    }

    private static int ceilToSlot(int minuteOfDay) {
        int remainder = minuteOfDay % SLOT_MINUTES;
        return remainder == 0 ? minuteOfDay : minuteOfDay + (SLOT_MINUTES - remainder);
    }

    private static int toMinuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime toLocalTime(int minuteOfDay) {
        int clamped = Math.max(0, Math.min(minuteOfDay, LAST_MINUTE_OF_DAY));
        return LocalTime.of(clamped / 60, clamped % 60);
    }
}
