package com.bujirun.bujirun.domain.itinerary.generate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// application.yaml의 transit: 하위 설정값 (택시 예상요금 + 이동수단 옵션 우선순위 기준)
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "transit")
public class TransitProperties {

    private Taxi taxi = new Taxi();
    private Priority priority = new Priority();

    @Getter
    @Setter
    public static class Taxi {
        private int baseFare;             // 기본요금
        private int baseDistanceM;        // 기본요금 적용 거리
        private int extraFare;            // 기본거리 초과 시 extraDistanceM마다 붙는 요금
        private int extraDistanceM;
        private List<NightSurcharge> nightSurcharges = new ArrayList<>();
    }

    // 심야 할증 구간 [startHour, endHour) 과 할증률(0.2 = 20%)
    @Getter
    @Setter
    public static class NightSurcharge {
        private int startHour;
        private int endHour;
        private double rate;
    }

    @Getter
    @Setter
    public static class Priority {
        private double walkDistanceThresholdM;  // 직선거리가 이 값 이하면 도보를 맨 앞에
        private int maxExtraCostPerPerson;      // 택시가 대중교통보다 1인당 이만큼까지 비싸도 허용
        private int minTimeSavedMinutes;        // 단, 이만큼 이상 빨라야 택시 우선
    }
}
