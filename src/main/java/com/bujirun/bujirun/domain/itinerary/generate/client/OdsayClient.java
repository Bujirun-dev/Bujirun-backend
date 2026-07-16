package com.bujirun.bujirun.domain.itinerary.generate.client;

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
    public JsonNode searchTransitRoute(double startX, double startY, double endX, double endY) {
        String uri = String.format(
                "/searchPubTransPathT?SX=%s&SY=%s&EX=%s&EY=%s&apiKey=%s",
                startX, startY, endX, endY,
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        );

        JsonNode result = webClient.get()
                .uri(uri)
                .header("Referer", "http://localhost:8080")  // 추가
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        log.info("ODsay API 호출 (캐시 미스) — SX:{} SY:{} EX:{} EY:{}", startX, startY, endX, endY);

        return result;
    }
}