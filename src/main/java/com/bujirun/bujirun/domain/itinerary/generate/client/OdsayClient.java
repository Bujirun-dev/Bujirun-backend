package com.bujirun.bujirun.domain.itinerary.generate.client;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDaySchedule;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayDeparture;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwaySchedule;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayTransferDetail;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubwayTransitInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OdsayClient {

    private final WebClient webClient;
    private final String apiKey;

    public OdsayClient(@Value("${odsay.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.odsay.com/v1/api")
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)
                ))
                .build();
    }

    /**
     * 두 좌표 간 대중교통 경로 조회
     *
     * @param startX 출발지 경도
     * @param startY 출발지 위도
     * @param endX   도착지 경도
     * @param endY   도착지 위도
     */

    @Cacheable(
            value = "odsayRoute",
            key = "T(String).format('%.6f:%.6f:%.6f:%.6f', #startX, #startY, #endX, #endY)",
            unless = "#result == null"
    )
    public TransitOption searchTransitRoute(double startX, double startY, double endX, double endY) { // 반환 타입 JsonNode → TransitOption
        JsonNode root = webClient.get() // 변수명 result → root (아래 parseTransit 호출용)
                .uri(uriBuilder -> uriBuilder
                        .path("/searchPubTransPathT")
                        .queryParam("SX", startX)
                        .queryParam("SY", startY)
                        .queryParam("EX", endX)
                        .queryParam("EY", endY)
                        .queryParam("apiKey", apiKey) // uriBuilder가 한 번만 인코딩하므로 여기서 직접 URLEncoder를 쓰면 이중 인코딩됨
                        .build())
                .header("Referer", "http://localhost:8080")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        log.info("ODsay API 호출 (캐시 미스) — SX:{} SY:{} EX:{} EY:{}", startX, startY, endX, endY);

        return parseTransit(root); // JsonNode 그대로 리턴하지 않고 파싱해서 TransitOption으로 리턴
    }


    // ransitRouteService에 있던 parseTransit 메서드를 통째로 이 클래스로 이동
    // remainMinutes 관련 enrich 로직은 제외 — 전부 null로 둔 채 리턴
    private TransitOption parseTransit(JsonNode root) {
        if (root == null) return null; // 추가: null 방어

        JsonNode pathArray = root.path("result").path("path"); // path.get(0) 이전에 배열 체크 추가
        if (!pathArray.isArray() || pathArray.isEmpty()) { // NPE 방지용 체크 강화
            log.warn("ODsay 경로 없음 (결과 path null) — 원본 응답: {}", root.toString());
            return null;
        }
        JsonNode path = pathArray.get(0);

        JsonNode info = path.path("info");
        List<SubPath> subPaths = new ArrayList<>();

        JsonNode subPathNodes = path.path("subPath");
        if (subPathNodes.isArray()) {
            for (JsonNode sub : subPathNodes) {
                int trafficType = sub.path("trafficType").asInt();
                if (trafficType == 3) {
                    subPaths.add(new SubPath(
                            "도보", sub.path("sectionTime").asInt(), "", 0,
                            "", "", 0, 0, 0, 0,
                            "", 0, 0, null
                    ));
                } else if (trafficType == 2) {
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

        // 기존 TransitRouteService에 있던 remainMinutes enrich(stream/map) 블록은
        // 여기로 옮기지 않음 — TransitRouteService.enrichWithArrival()로 이동함

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
                subPaths // enriched 아니라 subPaths 그대로 (remainMinutes는 전부 null 상태)
        );
    }

    /**
     * 지하철역 전체 배차 시각표 조회 (부산교통공사가 ODsay에 등록한 시각표 — GPS 기반 실시간 아님).
     *
     * @param stationId ODsay 지하철 역코드 (SubPath.startId())
     * @param wayCode   방면 코드 (1: 상행, 2: 하행)
     */
    @Cacheable(
            value = "odsaySubwaySchedule",
            key = "T(String).format('%d:%d', #stationId, #wayCode)",
            unless = "#result == null"
    )
    public SubwaySchedule searchSubwaySchedule(int stationId, int wayCode) {
        JsonNode root = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/searchSubwaySchedule")
                        .queryParam("apiKey", apiKey)
                        .queryParam("stationID", stationId)
                        .queryParam("wayCode", wayCode)
                        .build())
                .header("Referer", "http://localhost:8080")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        log.info("ODsay 지하철 시각표 조회 (캐시 미스) — stationId:{} wayCode:{}", stationId, wayCode);

        return parseSubwaySchedule(root);
    }

    private SubwaySchedule parseSubwaySchedule(JsonNode root) {
        if (root == null) return null;

        JsonNode result = root.path("result");
        if (result.isMissingNode()) {
            log.warn("ODsay 지하철 시각표 없음 — 원본 응답: {}", root);
            return null;
        }

        return new SubwaySchedule(
                result.path("stationName").asText(""),
                result.path("stationID").asInt(),
                parseDaySchedule(result.path("weekdaySchedule")),
                parseDaySchedule(result.path("saturdaySchedule")),
                parseDaySchedule(result.path("holidaySchedule"))
        );
    }

    private SubwayDaySchedule parseDaySchedule(JsonNode node) {
        if (node == null || node.isMissingNode()) return new SubwayDaySchedule(List.of(), List.of());
        return new SubwayDaySchedule(
                parseDepartures(node.path("up")),
                parseDepartures(node.path("down"))
        );
    }

    private List<SubwayDeparture> parseDepartures(JsonNode arrayNode) {
        if (!arrayNode.isArray()) return List.of();
        List<SubwayDeparture> departures = new ArrayList<>();
        for (JsonNode entry : arrayNode) {
            departures.add(new SubwayDeparture(
                    entry.path("departureTime").asText(""),
                    entry.path("subwayClass").asInt(0),
                    entry.path("firstLastFlag").asInt(0)
            ));
        }
        return departures;
    }

    /**
     * 지하철역 환승 정보 조회 (해당 역에서 갈아탈 수 있는 노선 안내 — 부산교통공사가 ODsay에 등록한 정적 데이터).
     *
     * @param stationId ODsay 지하철 역코드
     */
    @Cacheable(
            value = "odsaySubwayTransitInfo",
            key = "#stationId",
            unless = "#result == null"
    )
    public SubwayTransitInfo getSubwayTransitInfo(int stationId) {
        JsonNode root = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/subwayTransitInfo")
                        .queryParam("apiKey", apiKey)
                        .queryParam("stationID", stationId)
                        .build())
                .header("Referer", "http://localhost:8080")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        log.info("ODsay 지하철 환승정보 조회 (캐시 미스) — stationId:{}", stationId);

        return parseSubwayTransitInfo(root);
    }

    private SubwayTransitInfo parseSubwayTransitInfo(JsonNode root) {
        if (root == null) return null;

        JsonNode result = root.path("result");
        if (result.isMissingNode()) {
            log.warn("ODsay 지하철 환승정보 없음 — 원본 응답: {}", root);
            return null;
        }

        List<SubwayTransferDetail> details = new ArrayList<>();
        JsonNode list = result.path("transitTotalInfo");
        if (list.isArray()) {
            for (JsonNode entry : list) {
                details.add(new SubwayTransferDetail(
                        entry.path("takeStationID").asInt(),
                        entry.path("takeLaneName").asText(""),
                        entry.path("exStationID").asInt(),
                        entry.path("exLaneName").asText(""),
                        entry.hasNonNull("FastTrain") ? entry.path("FastTrain").asInt() : null,
                        entry.hasNonNull("FastFastDoor") ? entry.path("FastFastDoor").asInt() : null
                ));
            }
        }

        return new SubwayTransitInfo(result.path("count").asInt(details.size()), details);
    }
}