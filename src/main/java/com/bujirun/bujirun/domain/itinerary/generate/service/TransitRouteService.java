package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.OdsayClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import com.bujirun.bujirun.global.util.GeoUtils;
import com.bujirun.bujirun.global.util.TransitRouteUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransitRouteService {

    private final OdsayClient odsayClient;
    private final List<ArrivalInfoProvider> arrivalProviders;

    private static final double WALK_SPEED_MPS = 1.2;       // 도보 속도 1.2m/s
    private static final int TAXI_BASE_FARE = 4800;          // 기본요금
    private static final int TAXI_BASE_METER = 2000;         // 기본요금 적용 거리 (2km)
    private static final double TAXI_EXTRA_FARE_PER_M = 100.0 / 132.0; // 100원/132m

    private static final double ROAD_DISTANCE_FACTOR = 1.3;  // 차량용
    private static final double WALK_DISTANCE_FACTOR = 1.4;  // 도보용 (골목/계단 등 우회 반영)
    private static final int WALK_DISTANCE_THRESHOLD_M = 1000; // 이 거리(하버사인) 이상이면 도보 옵션 자체를 후보에서 제외

    public List<TransitRouteResponse> getRoutesForDay(List<SpotInfo> spots, String optimizationType) {
        List<TransitRouteResponse> routes = new ArrayList<>();

        Comparator<TransitOption> comparator = "TRANSFER_MIN".equals(optimizationType)
                ? Comparator.comparingInt(TransitOption::transferCount)
                : Comparator.comparingInt(TransitOption::totalTime);

        for (int i = 0; i < spots.size() - 1; i++) {
            SpotInfo from = spots.get(i);
            SpotInfo to = spots.get(i + 1);
            List<TransitOption> options = new ArrayList<>();

            // 대중교통 — 구조적 정보는 캐시에서, 도착정보는 매번 새로 enrich
            TransitOption transitOption = null;
            try {
                transitOption = odsayClient.searchTransitRoute(
                        from.getLng(), from.getLat(),
                        to.getLng(), to.getLat()
                );

                if (transitOption == null) {
                    log.info("ODsay 경로 없음 — 재시도 {} → {}", from.getName(), to.getName());
                    transitOption = odsayClient.searchTransitRoute(
                            from.getLng(), from.getLat(),
                            to.getLng(), to.getLat()
                    );
                }

                if (transitOption != null) {
                    transitOption = enrichWithArrival(transitOption);
                    options.add(transitOption);
                }
            } catch (Exception e) {
                log.warn("ODsay 경로 조회 실패 {} → {}: {}", from.getName(), to.getName(), e.getMessage());
            }

            // 도보 + 택시
            double distanceM = GeoUtils.haversineDistance(from.getLat(), from.getLng(), to.getLat(), to.getLng());

            if (distanceM <= WALK_DISTANCE_THRESHOLD_M) { // 도보 거리 임계값 초과 시 도보 옵션 자체를 후보에서 제외
                options.add(resolveWalkOption(distanceM, transitOption)); // ODsay 도보 구간 sectionTime 재사용, 매칭 실패 시 calcWalk 폴백
            }

            options.add(calcTaxi(distanceM));

            options.sort(comparator);
            routes.add(new TransitRouteResponse(options));
        }

        return routes;
    }

    /**
     * 캐시된(혹은 방금 조회한) TransitOption의 subPath들에 실시간 도착정보(remainMinutes)를 채운다.
     * 캐시 히트 여부와 무관하게 항상 새로 조회 — 도착정보는 절대 캐싱 대상이 아님.
     */
    private TransitOption enrichWithArrival(TransitOption option) {
        List<SubPath> enriched = option.subPaths().stream().map(sp -> {
            if ("도보".equals(sp.type())) return sp;
            Integer remain = arrivalProviders.stream()
                    .filter(p -> p.supports(sp.type()))
                    .findFirst()
                    .map(p -> p.getNextArrival(sp))
                    .orElse(null);
            return new SubPath(
                    sp.type(), sp.sectionTime(), sp.routeNo(), sp.stationCount(),
                    sp.startName(), sp.endName(),
                    sp.startX(), sp.startY(), sp.endX(), sp.endY(),
                    sp.startArsId(), sp.startId(), sp.wayCode(),
                    remain
            );
        }).toList();

        return new TransitOption(
                option.type(), option.totalTime(), option.totalFare(),
                option.transferCount(), option.estimated(),
                enriched
        );
    }

    private TransitOption calcWalk(double distanceM) {
        double walkDistanceM = distanceM * WALK_DISTANCE_FACTOR;
        int timeMin = (int) Math.ceil(walkDistanceM / WALK_SPEED_MPS / 60);
        return new TransitOption("도보", timeMin, 0, 0, true, List.of());
    }

    // ODsay 응답이 전 구간 도보(trafficType 3)로만 구성된 경우 그 sectionTime 합을
    // 도보 소요시간으로 재사용한다. ODsay 응답이 없거나 도보 전용 매칭이 아니면 calcWalk()로 폴백.
    private TransitOption resolveWalkOption(double distanceM, TransitOption transitOption) {
        List<SubPath> subPaths = transitOption != null ? transitOption.subPaths() : List.of();
        boolean isWalkOnlyRoute = !subPaths.isEmpty()
                && TransitRouteUtils.findFirstTransitSubPath(subPaths) == null;

        if (isWalkOnlyRoute) {
            int sectionTimeSum = subPaths.stream().mapToInt(SubPath::sectionTime).sum();
            return new TransitOption("도보", sectionTimeSum, 0, 0, false, subPaths);
        }

        return calcWalk(distanceM); // ODsay 매칭 실패 시 기존 계산식으로 폴백
    }

    private TransitOption calcTaxi(double distanceM) {
        double roadDistanceM = distanceM * ROAD_DISTANCE_FACTOR;

        int fare;
        if (roadDistanceM <= TAXI_BASE_METER) {
            fare = TAXI_BASE_FARE;
        } else {
            fare = TAXI_BASE_FARE + (int) ((roadDistanceM - TAXI_BASE_METER) * TAXI_EXTRA_FARE_PER_M);
        }
        int timeMin = (int) Math.ceil(roadDistanceM / 1000 / 30 * 60);
        return new TransitOption("택시", timeMin, fare, 0, true, List.of());
    }

}