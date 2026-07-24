package com.bujirun.bujirun.domain.itinerary.generate.client;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        String uri = String.format(
                "/searchPubTransPathT?SX=%s&SY=%s&EX=%s&EY=%s&apiKey=%s",
                startX, startY, endX, endY,
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        );

        JsonNode root = webClient.get() // 변수명 result → root (아래 parseTransit 호출용)
                .uri(uri)
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
}