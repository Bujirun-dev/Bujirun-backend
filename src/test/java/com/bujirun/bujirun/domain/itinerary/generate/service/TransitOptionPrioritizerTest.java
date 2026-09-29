package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.config.TransitProperties;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransitOptionPrioritizerTest {

    private static final double FAR_DISTANCE_M = 3000;
    private static final int TRANSIT_FARE = 1550;

    private TransitOptionPrioritizer prioritizer;
    private TaxiFareEstimator fareEstimator;

    @BeforeEach
    void setUp() {
        TransitProperties properties = new TransitProperties();
        properties.getTaxi().setBaseFare(4800);
        properties.getTaxi().setBaseDistanceM(2000);
        properties.getTaxi().setExtraFare(100);
        properties.getTaxi().setExtraDistanceM(132);
        properties.getTaxi().setNightSurcharges(List.of(
                nightSurcharge(23, 24, 0.2),
                nightSurcharge(0, 2, 0.3),
                nightSurcharge(2, 4, 0.2)
        ));
        properties.getPriority().setWalkDistanceThresholdM(700);
        properties.getPriority().setMaxExtraCostPerPerson(1000);
        properties.getPriority().setMinTimeSavedMinutes(10);

        prioritizer = new TransitOptionPrioritizer(properties);
        fareEstimator = new TaxiFareEstimator(properties);
    }

    // 택시 4,800원·12분 vs 지하철 1,550원·35분 (23분 절약)
    private List<TransitOption> options(int taxiFare, int taxiTime) {
        return List.of(
                option("버스", 40, TRANSIT_FARE),
                option("도보", 45, 0),
                option("택시", taxiTime, taxiFare),
                option("지하철", 35, TRANSIT_FARE)
        );
    }

    @Test
    void 한_명이면_택시_추가비용이_커서_대중교통_우선() {
        List<TransitOption> result = prioritizer.prioritize(options(4800, 12), FAR_DISTANCE_M, 1);

        assertThat(types(result)).containsExactly("지하철", "버스", "택시", "도보");
        assertThat(prioritizer.taxiFarePerPerson(4800, 1)).isEqualTo(4800);
    }

    @Test
    void 두_명이면_추가비용이_허용범위이고_시간을_충분히_아껴_택시_우선() {
        // 1인당 2,400원 - 1,550원 = 850원 추가, 23분 절약
        List<TransitOption> result = prioritizer.prioritize(options(4800, 12), FAR_DISTANCE_M, 2);

        assertThat(types(result)).containsExactly("택시", "지하철", "버스", "도보");
        assertThat(prioritizer.taxiFarePerPerson(4800, 2)).isEqualTo(2400);
    }

    @Test
    void 네_명이면_택시가_1인당_더_싸서_시간_절약과_무관하게_택시_우선() {
        // 1인당 1,200원 < 1,550원, 절약 시간 5분
        List<TransitOption> result = prioritizer.prioritize(options(4800, 30), FAR_DISTANCE_M, 4);

        assertThat(types(result)).containsExactly("택시", "지하철", "버스", "도보");
        assertThat(prioritizer.taxiFarePerPerson(4800, 4)).isEqualTo(1200);
    }

    @Test
    void 다섯_명이면_택시_2대_요금을_나눠_추가비용이_생기고_절약시간이_적으면_대중교통_우선() {
        // 2대 × 4,800원 / 5명 = 1,920원 → 370원 추가, 절약 시간 5분 < 10분
        List<TransitOption> result = prioritizer.prioritize(options(4800, 30), FAR_DISTANCE_M, 5);

        assertThat(types(result)).containsExactly("지하철", "버스", "택시", "도보");
        assertThat(prioritizer.taxiFarePerPerson(4800, 5)).isEqualTo(1920);
    }

    @Test
    void 심야_할증이_붙으면_같은_인원이라도_대중교통_우선으로_바뀐다() {
        int dayFare = fareEstimator.estimate(2000, LocalDateTime.of(2026, 9, 30, 14, 0));
        int nightFare = fareEstimator.estimate(2000, LocalDateTime.of(2026, 9, 30, 23, 0));
        assertThat(dayFare).isEqualTo(4800);
        assertThat(nightFare).isEqualTo(5800); // 4,800 × 1.2 = 5,760 → 100원 단위 올림

        // 2명 기준 낮: 1인당 2,400원(850원 추가) → 택시 / 밤: 2,900원(1,350원 추가) → 대중교통
        assertThat(prioritizer.prioritize(options(dayFare, 12), FAR_DISTANCE_M, 2).get(0).type()).isEqualTo("택시");
        assertThat(prioritizer.prioritize(options(nightFare, 12), FAR_DISTANCE_M, 2).get(0).type()).isEqualTo("지하철");
    }

    @Test
    void 심야_할증은_23시_20퍼센트_0시부터_2시_30퍼센트_2시부터_4시_20퍼센트() {
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 9, 30, 22, 59))).isEqualTo(4800);
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 9, 30, 23, 0))).isEqualTo(5800);  // 5,760 → 올림
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 10, 1, 0, 0))).isEqualTo(6300);   // 6,240 → 올림
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 10, 1, 1, 59))).isEqualTo(6300);
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 10, 1, 2, 0))).isEqualTo(5800);
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 10, 1, 3, 59))).isEqualTo(5800);
        assertThat(fareEstimator.estimate(2000, LocalDateTime.of(2026, 10, 1, 4, 0))).isEqualTo(4800);
    }

    @Test
    void 기본거리를_넘으면_거리요금이_붙는다() {
        // 3,320m = 기본 2,000m + 1,320m(132m × 10) → 4,800 + 1,000
        assertThat(fareEstimator.estimate(3320, LocalDateTime.of(2026, 9, 30, 14, 0))).isEqualTo(5800);
    }

    @Test
    void 도보_임계값_이하면_택시가_유리해도_도보가_맨_앞() {
        List<TransitOption> result = prioritizer.prioritize(options(4800, 12), 700, 4);

        assertThat(types(result)).containsExactly("도보", "택시", "지하철", "버스");
    }

    @Test
    void 도보_임계값을_넘으면_도보는_맨_뒤() {
        List<TransitOption> result = prioritizer.prioritize(options(4800, 12), 701, 1);

        assertThat(types(result)).containsExactly("지하철", "버스", "택시", "도보");
    }

    @Test
    void 대중교통_경로가_없으면_택시_우선() {
        List<TransitOption> result = prioritizer.prioritize(
                List.of(option("도보", 45, 0), option("택시", 12, 4800)), FAR_DISTANCE_M, 1);

        assertThat(types(result)).containsExactly("택시", "도보");
    }


    private static TransitProperties.NightSurcharge nightSurcharge(int startHour, int endHour, double rate) {
        TransitProperties.NightSurcharge surcharge = new TransitProperties.NightSurcharge();
        surcharge.setStartHour(startHour);
        surcharge.setEndHour(endHour);
        surcharge.setRate(rate);
        return surcharge;
    }

    private static TransitOption option(String type, int totalTime, int totalFare) {
        return new TransitOption(type, totalTime, totalFare, 0, false, List.of());
    }

    private static List<String> types(List<TransitOption> options) {
        return options.stream().map(TransitOption::type).toList();
    }

}
