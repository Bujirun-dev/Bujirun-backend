package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.OdsayClient;
import com.bujirun.bujirun.domain.itinerary.generate.config.TransitProperties;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransitRouteServiceTest {

    // 2026-09-30은 수요일
    private static final LocalDateTime WEEKDAY_RUSH_HOUR = LocalDateTime.of(2026, 9, 30, 8, 0);
    private static final LocalDateTime WEEKDAY_NIGHT = LocalDateTime.of(2026, 9, 30, 23, 0);

    // 서면 → 해운대 (직선 약 9km)
    private final SpotInfo from = spot(35.1578, 129.0600);
    private final SpotInfo to = spot(35.1587, 129.1604);

    private TransitRouteService service;

    @BeforeEach
    void setUp() {
        TransitProperties properties = new TransitProperties();
        properties.getTaxi().setBaseFare(4800);
        properties.getTaxi().setBaseDistanceM(2000);
        properties.getTaxi().setExtraFare(100);
        properties.getTaxi().setExtraDistanceM(132);

        OdsayClient odsayClient = mock(OdsayClient.class);
        when(odsayClient.searchTransitRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        service = new TransitRouteService(odsayClient, List.of(),
                new TaxiFareEstimator(properties), new TransitOptionPrioritizer(properties));
    }

    @Test
    void 택시_소요시간은_이동_예정_시각의_혼잡도를_따른다() {
        int rushHour = service.estimateTaxi(from, to, WEEKDAY_RUSH_HOUR).totalTime();
        int night = service.estimateTaxi(from, to, WEEKDAY_NIGHT).totalTime();

        assertThat(rushHour).isGreaterThan(night);
    }

    @Test
    void 같은_기준_시각이면_저장용_경로와_옵션_API의_택시_시간이_같다() {
        TransitOption saved = taxiOf(service.getRoutesForDay(List.of(from, to), null, WEEKDAY_RUSH_HOUR)
                .get(0).options());
        TransitOption listed = taxiOf(service.getPrioritizedOptions(from, to, 1, WEEKDAY_RUSH_HOUR));

        assertThat(listed.totalTime()).isEqualTo(saved.totalTime());
        assertThat(listed.totalFare()).isEqualTo(saved.totalFare());
    }

    @Test
    void 도착_시각이_정해지면_택시_옵션만_구간별_시각으로_다시_계산한다() {
        List<TransitRouteResponse> routes = service.getRoutesForDay(List.of(from, to), null, WEEKDAY_NIGHT);

        List<TransitRouteResponse> retimed = service.retimeTaxiOptions(
                List.of(from, to), routes, List.of(WEEKDAY_RUSH_HOUR));

        assertThat(taxiOf(retimed.get(0).options()).totalTime())
                .isEqualTo(service.estimateTaxi(from, to, WEEKDAY_RUSH_HOUR).totalTime());
        // 택시 외 옵션과 옵션 순서는 그대로 둔다
        assertThat(retimed.get(0).options()).extracting(TransitOption::type)
                .containsExactlyElementsOf(routes.get(0).options().stream().map(TransitOption::type).toList());
    }

    @Test
    void 기준_시각은_일정_날짜와_도착_예정_시각으로_만든다() {
        assertThat(TransitRouteService.toTravelAt(LocalDate.of(2026, 9, 30), LocalTime.of(8, 0)))
                .isEqualTo(WEEKDAY_RUSH_HOUR);
        assertThat(TransitRouteService.toTravelAt(LocalDate.of(2026, 9, 30), null)).isNull();
    }

    private TransitOption taxiOf(List<TransitOption> options) {
        return options.stream().filter(opt -> "택시".equals(opt.type())).findFirst().orElseThrow();
    }

    private static SpotInfo spot(double lat, double lng) {
        return SpotInfo.builder().name("spot").lat(lat).lng(lng).build();
    }
}
