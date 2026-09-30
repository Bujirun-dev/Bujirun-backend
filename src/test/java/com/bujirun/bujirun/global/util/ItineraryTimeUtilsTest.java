package com.bujirun.bujirun.global.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 유틸이 만들어내는 값은 그대로 DB(itinerary_items.arrival_time)에 저장되고
 * 로그·Yjs로도 퍼지므로, 규칙(자정 차단·종료 시각 상한·중복 없는 증가·10분 단위)을 고정해둔다.
 */
class ItineraryTimeUtilsTest {

    // ── 자정 초과 ──────────────────────────────────────────────────

    @Test
    void 누적이_자정을_넘겨도_새벽_시각으로_돌지_않고_하루_마지막_슬롯에서_멈춘다() {
        // 22:00 + (90분 × 2) = 01:00이 되던 케이스(LocalTime.plusMinutes는 자정에서 한 바퀴 돈다).
        List<LocalTime> arrivals = ItineraryTimeUtils.accumulateArrivalTimes(
                LocalTime.of(22, 0), List.of(90, 90), null);

        assertThat(arrivals).containsExactly(
                LocalTime.of(22, 0), LocalTime.of(23, 30), ItineraryTimeUtils.LAST_SLOT_OF_DAY);
    }

    @Test
    void 상한이_이르고_항목이_많아도_순서가_유지되고_시각이_겹치지_않는다() {
        List<LocalTime> arrivals = ItineraryTimeUtils.accumulateArrivalTimes(
                LocalTime.of(23, 0), List.of(60, 60, 60, 60), null);

        assertThat(arrivals).containsExactly(
                LocalTime.of(23, 0), LocalTime.of(23, 20), LocalTime.of(23, 30),
                LocalTime.of(23, 40), LocalTime.of(23, 50));
        assertThat(arrivals).isSorted();
        assertThat(arrivals).doesNotHaveDuplicates();
    }

    // ── 종료 시각 상한 몰림 ────────────────────────────────────────

    @Test
    void 종료_시각_상한에_몰리면_상한을_넘지_않고_최소_간격으로_당겨진다() {
        // 19:00 시작 + 120분 간격 두 번이면 21:00/23:00이지만 종료 시각(20:00)이 상한이다.
        List<LocalTime> arrivals = ItineraryTimeUtils.accumulateArrivalTimes(
                LocalTime.of(19, 0), List.of(120, 120), LocalTime.of(20, 0));

        assertThat(arrivals).containsExactly(
                LocalTime.of(19, 0), LocalTime.of(19, 50), LocalTime.of(20, 0));
    }

    @Test
    void 종료_시각_상한은_마지막_날에만_적용된다() {
        // 3일 여행의 1·2일차는 종료 시각과 무관하게 자정 직전까지, 마지막 날만 종료 시각이 상한.
        assertThat(ItineraryTimeUtils.resolveDayEndLimit(1, 3, LocalTime.of(18, 0)))
                .isEqualTo(ItineraryTimeUtils.LAST_SLOT_OF_DAY);
        assertThat(ItineraryTimeUtils.resolveDayEndLimit(3, 3, LocalTime.of(18, 0)))
                .isEqualTo(LocalTime.of(18, 0));
        // totalDays를 모르면(0 이하) 마지막 날로 본다.
        assertThat(ItineraryTimeUtils.resolveDayEndLimit(1, 0, LocalTime.of(18, 0)))
                .isEqualTo(LocalTime.of(18, 0));
    }

    // ── null / 미지정(00:00) 섞임 ──────────────────────────────────

    @Test
    void 간격이나_시작_시각에_null이_섞여도_최소_간격을_지키며_계산된다() {
        List<LocalTime> withNullGap = ItineraryTimeUtils.accumulateArrivalTimes(
                LocalTime.of(9, 0), Arrays.asList(null, 30), LocalTime.of(23, 50));
        assertThat(withNullGap).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(9, 10), LocalTime.of(9, 40));

        // 시작 시각이 없으면 기본값(09:00)
        assertThat(ItineraryTimeUtils.accumulateArrivalTimes(null, List.of(60), null))
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));

        // 간격 목록 자체가 없으면 시작 시각 하나
        assertThat(ItineraryTimeUtils.accumulateArrivalTimes(LocalTime.of(13, 0), null, null))
                .containsExactly(LocalTime.of(13, 0));

        // 상한이 없으면(null) 자정 직전까지만
        assertThat(ItineraryTimeUtils.accumulateArrivalTimes(LocalTime.of(23, 40), List.of(60), null))
                .containsExactly(LocalTime.of(23, 40), ItineraryTimeUtils.LAST_SLOT_OF_DAY);
    }

    @Test
    void 여행_시작_종료_시각이_자정이면_설정_안_된_것으로_보고_기본값을_쓴다() {
        // 프론트 scheduleUtils.boundMinutes와 같은 규칙 — 시간이 비어 있는 일정을 여행 수정
        // 모달에서 저장하면 00:00이 들어오는데, 이걸 진짜 자정으로 읽으면 마지막 날 항목이
        // 전부 00:00/00:10/00:20으로 눌린다.
        assertThat(ItineraryTimeUtils.resolveDayStartTime(1, LocalTime.MIDNIGHT))
                .isEqualTo(ItineraryTimeUtils.DEFAULT_DAY_START);
        assertThat(ItineraryTimeUtils.resolveDayStartTime(1, null))
                .isEqualTo(ItineraryTimeUtils.DEFAULT_DAY_START);
        assertThat(ItineraryTimeUtils.resolveDayEndLimit(1, 1, LocalTime.MIDNIGHT))
                .isEqualTo(ItineraryTimeUtils.LAST_SLOT_OF_DAY);
        assertThat(ItineraryTimeUtils.resolveDayEndLimit(1, 1, null))
                .isEqualTo(ItineraryTimeUtils.LAST_SLOT_OF_DAY);
    }

    // ── 시작 기준 시각 · 10분 단위 ─────────────────────────────────

    @Test
    void 여행_시작_시각은_첫날에만_기준으로_쓰인다() {
        assertThat(ItineraryTimeUtils.resolveDayStartTime(1, LocalTime.of(20, 0)))
                .isEqualTo(LocalTime.of(20, 0));
        assertThat(ItineraryTimeUtils.resolveDayStartTime(2, LocalTime.of(20, 0)))
                .isEqualTo(ItineraryTimeUtils.DEFAULT_DAY_START);
    }

    @Test
    void 새로_만드는_시각은_10분_단위로_맞춘다() {
        // 프론트가 표시할 때 10분 단위로 반올림하므로, 저장값도 10분 단위여야 화면과 갈리지 않는다.
        List<LocalTime> arrivals = ItineraryTimeUtils.accumulateArrivalTimes(
                LocalTime.of(9, 3), List.of(7), LocalTime.of(23, 50));

        assertThat(arrivals).containsExactly(LocalTime.of(9, 10), LocalTime.of(9, 20));
        assertThat(arrivals).allSatisfy(time -> assertThat(time.getMinute() % 10).isZero());
    }

    // ── 확정 직후 기본 배치 (프론트 tests/itinerary-default-times.test.mjs와 같은 케이스) ──

    private static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    @Test
    void 기본_배치는_10시부터_3시간_간격이다() {
        assertThat(ItineraryTimeUtils.defaultVisitTimes(2, 3, 3, null, null))
                .containsExactly(t("10:00"), t("13:00"), t("16:00"));
        assertThat(ItineraryTimeUtils.defaultVisitTimes(2, 3, 1, null, null))
                .containsExactly(t("10:00"));
        assertThat(ItineraryTimeUtils.defaultVisitTimes(2, 3, 4, null, null))
                .containsExactly(t("10:00"), t("13:00"), t("16:00"), t("19:00"));
    }

    @Test
    void 첫날_여행_시작이_이르면_10시_늦으면_그_시각부터_3시간_간격() {
        assertThat(ItineraryTimeUtils.defaultVisitTimes(1, 2, 2, t("08:00"), t("18:00")))
                .containsExactly(t("10:00"), t("13:00"));
        // 2026-09-30 사고 일정: 시작 09:40이어도 10:00부터 (예전엔 09:40 10:50 12:00처럼 이동시간 누적)
        assertThat(ItineraryTimeUtils.defaultVisitTimes(1, 4, 3, t("09:40"), t("21:40")))
                .containsExactly(t("10:00"), t("13:00"), t("16:00"));
        assertThat(ItineraryTimeUtils.defaultVisitTimes(1, 2, 1, t("20:00"), t("18:00")))
                .containsExactly(t("20:00"));
        assertThat(ItineraryTimeUtils.defaultVisitTimes(1, 2, 2, t("20:00"), t("18:00")))
                .containsExactly(t("20:00"), t("23:00"));
    }

    @Test
    void 마지막_날은_여행_종료_시각을_넘지_않게_간격만_좁힌다() {
        assertThat(ItineraryTimeUtils.defaultVisitTimes(2, 2, 3, t("10:00"), t("14:00")))
                .containsExactly(t("10:00"), t("12:00"), t("14:00"));
        // 종료 시각이 하루 시작보다 이르면 종료 시각에서 거꾸로 펼친다
        assertThat(ItineraryTimeUtils.defaultVisitTimes(2, 2, 2, t("10:00"), t("09:00")))
                .containsExactly(t("08:50"), t("09:00"));
    }

    @Test
    void 여행_시작_종료가_자정이면_설정_안_된_것으로_본다() {
        assertThat(ItineraryTimeUtils.defaultVisitTimes(1, 1, 3, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT))
                .containsExactly(t("10:00"), t("13:00"), t("16:00"));
    }

    @Test
    void 기본_배치는_어떤_경계에서도_겹치지_않고_오름차순이며_23시50분을_넘지_않는다() {
        List<String> stamps = List.of("00:00", "07:30", "10:00", "13:20", "18:00", "20:00", "23:30");
        int[][] dayCases = {{1, 1}, {1, 3}, {2, 3}, {3, 3}};
        for (String start : stamps) {
            for (String end : stamps) {
                for (int count = 1; count <= 8; count++) {
                    for (int[] dayCase : dayCases) {
                        List<LocalTime> result = ItineraryTimeUtils.defaultVisitTimes(
                                dayCase[0], dayCase[1], count, t(start), t(end));
                        String label = start + "~" + end + " " + count + "곳 day" + dayCase[0] + "/" + dayCase[1];
                        assertThat(result).as(label).hasSize(count).doesNotHaveDuplicates().isSorted();
                        assertThat(result.get(count - 1)).as(label).isBeforeOrEqualTo(ItineraryTimeUtils.LAST_SLOT_OF_DAY);
                    }
                }
            }
        }
    }
}
