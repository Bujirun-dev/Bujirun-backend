package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.OdsayClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import com.bujirun.bujirun.global.util.GeoUtils;
import com.fasterxml.jackson.databind.JsonNode;
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

    public List<TransitRouteResponse> getRoutesForDay(List<SpotInfo> spots, String optimizationType) {
        List<TransitRouteResponse> routes = new ArrayList<>();

        Comparator<TransitOption> comparator = "TRANSFER_MIN".equals(optimizationType)
                ? Comparator.comparingInt(TransitOption::transferCount)
                : Comparator.comparingInt(TransitOption::totalTime);

        for (int i = 0; i < spots.size() - 1; i++) {
            SpotInfo from = spots.get(i);
            SpotInfo to = spots.get(i + 1);
            List<TransitOption> options = new ArrayList<>();

            // 대중교통
            try {
                JsonNode result = odsayClient.searchTransitRoute(
                        from.getLng(), from.getLat(),
                        to.getLng(), to.getLat()
                );
                TransitOption transitOption = parseTransit(result);

                // 추가: null이면 1회 재시도
                if (transitOption == null) {
                    log.info("ODsay 경로 없음 — 재시도 {} → {}", from.getName(), to.getName());
                    result = odsayClient.searchTransitRoute(
                            from.getLng(), from.getLat(),
                            to.getLng(), to.getLat()
                    );
                    transitOption = parseTransit(result);
                }

                if (transitOption != null) options.add(transitOption);
            } catch (Exception e) {
                log.warn("ODsay 경로 조회 실패 {} → {}: {}", from.getName(), to.getName(), e.getMessage());
            }

            // 도보 + 택시
            double distanceM = GeoUtils.haversineDistance(from.getLat(), from.getLng(), to.getLat(), to.getLng());

            options.add(calcWalk(distanceM));
            options.add(calcTaxi(distanceM));

            options.sort(comparator);
            routes.add(new TransitRouteResponse(options));
        }

        return routes;
    }

    private TransitOption parseTransit(JsonNode root) {
        JsonNode path = root.path("result").path("path").get(0);
        if (path == null) {
            log.warn("ODsay 경로 없음 (결과 path null)");
            return null;
        }

        JsonNode info = path.path("info");
        List<SubPath> subPaths = new ArrayList<>();

        JsonNode subPathNodes = path.path("subPath");
        if (subPathNodes.isArray()) {
            for (JsonNode sub : subPathNodes) {
                int trafficType = sub.path("trafficType").asInt();
                if (trafficType == 3) {
                    // 도보 구간 — 정류장 정보 없음
                    subPaths.add(new SubPath(
                            "도보", sub.path("sectionTime").asInt(), "", 0,
                            "", "", 0, 0, 0, 0,
                            "", 0, 0, null
                    ));
                } else if (trafficType == 2) {
                    // 버스 구간
                    String busNo = sub.path("lane").get(0).path("busNo").asText();
                    subPaths.add(new SubPath(
                            "버스", sub.path("sectionTime").asInt(), busNo, sub.path("stationCount").asInt(),
                            sub.path("startName").asText(),
                            sub.path("endName").asText(),
                            sub.path("startX").asDouble(),
                            sub.path("startY").asDouble(),
                            sub.path("endX").asDouble(),
                            sub.path("endY").asDouble(),
                            sub.path("startArsID").asText(""),
                            0, 0, null
                    ));
                } else if (trafficType == 1) {
                    // 지하철 구간
                    String lineName = sub.path("lane").get(0).path("name").asText();
                    subPaths.add(new SubPath(
                            "지하철", sub.path("sectionTime").asInt(), lineName, sub.path("stationCount").asInt(),
                            sub.path("startName").asText(),
                            sub.path("endName").asText(),
                            sub.path("startX").asDouble(),
                            sub.path("startY").asDouble(),
                            sub.path("endX").asDouble(),
                            sub.path("endY").asDouble(),
                            "",
                            sub.path("startID").asInt(),
                            sub.path("wayCode").asInt(),
                            null
                    ));
                }
            }
        }

        // remainMinutes
        List<SubPath> enriched = subPaths.stream().map(sp -> {
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


        log.info("ODsay 경로 조회 성공 — {}분 · {}원 · 환승{}회",
                info.path("totalTime").asInt(),
                info.path("payment").asInt(),
                info.path("busTransitCount").asInt() + info.path("subwayTransitCount").asInt());

        return new TransitOption(
                "대중교통",
                info.path("totalTime").asInt(),
                info.path("payment").asInt(),
                info.path("busTransitCount").asInt() + info.path("subwayTransitCount").asInt(),
                false,
//                subPaths
                enriched
        );
    }

    private TransitOption calcWalk(double distanceM) {
        int timeMin = (int) Math.ceil(distanceM / WALK_SPEED_MPS / 60);
        return new TransitOption("도보", timeMin, 0, 0, true, List.of());
    }

    private TransitOption calcTaxi(double distanceM) {
        int fare;
        if (distanceM <= TAXI_BASE_METER) {
            fare = TAXI_BASE_FARE;
        } else {
            fare = TAXI_BASE_FARE + (int) ((distanceM - TAXI_BASE_METER) * TAXI_EXTRA_FARE_PER_M);
        }
        int timeMin = (int) Math.ceil(distanceM / 1000 / 30 * 60);
        return new TransitOption("택시", timeMin, fare, 0, true, List.of());
    }

}