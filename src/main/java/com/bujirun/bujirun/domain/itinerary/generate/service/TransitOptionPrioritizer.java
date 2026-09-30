package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.config.TransitProperties;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 이동수단 옵션 표시 순서를 정한다. 요금은 총액 그대로 두고, 순서만 1인당 비용 기준으로 판단한다.
 * 직선거리가 도보 임계값 이하면 도보를 맨 앞에, 넘으면 맨 뒤에 둔다.
 * 그 사이에서는 가장 빠른 대중교통과 택시를 1인당 비용·절약 시간으로 비교해 택시/대중교통 중 앞에 올 쪽을 정한다.
 * 옵션은 빠짐없이 모두 돌려주고 순서만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class TransitOptionPrioritizer {

    private static final String WALK = "도보";
    private static final String TAXI = "택시";
    private static final int TAXI_CAPACITY = 4;

    private final TransitProperties properties;

    public List<TransitOption> prioritize(List<TransitOption> options, double distanceM, int partySize) {
        int n = Math.max(partySize, 1);

        TransitOption walk = findByType(options, WALK);
        TransitOption taxi = findByType(options, TAXI);
        List<TransitOption> transits = options.stream()
                .filter(opt -> !WALK.equals(opt.type()) && !TAXI.equals(opt.type()))
                .sorted(Comparator.comparingInt(TransitOption::totalTime))
                .toList();

        boolean walkFirst = walk != null && distanceM <= properties.getPriority().getWalkDistanceThresholdM();
        boolean taxiFirst = taxi != null && (transits.isEmpty() || isTaxiPreferred(taxi, transits.get(0), n));

        List<TransitOption> ordered = new ArrayList<>();
        if (walkFirst) ordered.add(walk);
        if (taxiFirst) ordered.add(taxi);
        ordered.addAll(transits);
        if (taxi != null && !taxiFirst) ordered.add(taxi);
        if (walk != null && !walkFirst) ordered.add(walk);

        return ordered;
    }

    // 추가 비용이 없거나, 허용 범위 안의 추가 비용으로 충분한 시간을 아끼면 택시 우선
    boolean isTaxiPreferred(TransitOption taxi, TransitOption fastestTransit, int partySize) {
        TransitProperties.Priority priority = properties.getPriority();
        int extraCost = taxiFarePerPerson(taxi.totalFare(), partySize) - fastestTransit.totalFare();
        int timeSaved = fastestTransit.totalTime() - taxi.totalTime();

        return extraCost <= 0
                || (extraCost <= priority.getMaxExtraCostPerPerson() && timeSaved >= priority.getMinTimeSavedMinutes());
    }

    // 택시 1대 정원 4명 — 5명이면 2대 요금을 5명이 나눈다
    int taxiFarePerPerson(int taxiFare, int partySize) {
        int taxiCount = (partySize + TAXI_CAPACITY - 1) / TAXI_CAPACITY;
        return (int) Math.ceil((double) taxiCount * taxiFare / partySize);
    }

    private TransitOption findByType(List<TransitOption> options, String type) {
        return options.stream().filter(opt -> type.equals(opt.type())).findFirst().orElse(null);
    }
}
