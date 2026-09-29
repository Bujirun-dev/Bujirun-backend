package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.config.TransitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// 택시 예상요금 = 기본요금 + 거리요금, 심야 시간대별 할증 반영 후 100원 단위 올림
// 부산 내 이동만 다루므로 시계외 할증은 적용하지 않는다
// 기준 시각을 인자로 받아서 할증 여부를 테스트에서 고정할 수 있게 한다
@Component
@RequiredArgsConstructor
public class TaxiFareEstimator {

    private final TransitProperties properties;

    public int estimate(double roadDistanceM, LocalDateTime at) {
        TransitProperties.Taxi taxi = properties.getTaxi();

        int fare = taxi.getBaseFare();
        if (roadDistanceM > taxi.getBaseDistanceM()) {
            fare += (int) ((roadDistanceM - taxi.getBaseDistanceM()) * taxi.getExtraFare() / taxi.getExtraDistanceM());
        }

        double surcharge = 1 + resolveNightSurchargeRate(taxi, at.getHour());
        return (int) (Math.ceil(fare * surcharge / 100.0) * 100);
    }

    private double resolveNightSurchargeRate(TransitProperties.Taxi taxi, int hour) {
        return taxi.getNightSurcharges().stream()
                .filter(s -> hour >= s.getStartHour() && hour < s.getEndHour())
                .mapToDouble(TransitProperties.NightSurcharge::getRate)
                .findFirst()
                .orElse(0);
    }
}
