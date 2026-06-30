package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Slf4j
@Service
public class SubwayArrivalService implements ArrivalInfoProvider {

    private final WebClient webClient;
    private final String apiKey;

    public SubwayArrivalService(@Value("${odsay.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)
                ))
                .build();
    }

    @Override
    public boolean supports(String type) {
        return "지하철".equals(type);
    }

    @Override
    public Integer getNextArrival(SubPath subPath) {
        if (subPath.startId() == 0) {
            log.info("지하철 역코드 없음 — {}", subPath.startName());
            return null;
        }
        try {
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            JsonNode result = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.odsay.com")
                            .path("/v1/api/searchSubwaySchedule")
                            .queryParam("apiKey", encodedKey)
                            .queryParam("stationID", subPath.startId())
                            .queryParam("wayCode", subPath.wayCode())
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return parseNextArrival(result, subPath.wayCode());
        } catch (Exception e) {
            log.warn("지하철 시각표 조회 실패 statnId={}: {}", subPath.startId(), e.getMessage());
            return null;
        }
    }

    private Integer parseNextArrival(JsonNode root, int wayCode) {
        if (root == null || root.path("result").isMissingNode()) return null;

        JsonNode schedule = resolveSchedule(root.path("result"));
        if (schedule == null) return null;

        String direction = wayCode == 1 ? "up" : "down";
        JsonNode times = schedule.path(direction);
        if (!times.isArray()) return null;

        LocalTime now = LocalTime.now();

        for (JsonNode entry : times) {
            String departureTime = entry.path("departureTime").asText();
            if (departureTime.isBlank()) continue;

            // departureTime 형식: "HH:mm" 또는 "H:mm"
            try {
                String[] parts = departureTime.split(":");
                int hour = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);
                if (hour >= 24) hour -= 24;

                LocalTime trainTime = LocalTime.of(hour, min);
                int diff = (int) java.time.Duration.between(now, trainTime).toMinutes();
                if (diff >= 0) {
                    log.info("다음 지하철 → {}분 후 ({}:{})", diff, hour, min);
                    return diff;
                }
            } catch (Exception e) {
                log.warn("시각 파싱 실패: {}", departureTime);
            }
        }

        log.warn("오늘 남은 열차 없음");
        return null;
    }

    private JsonNode resolveSchedule(JsonNode result) {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) return result.path("saturdaySchedule");
        if (day == DayOfWeek.SUNDAY) return result.path("holidaySchedule");
        return result.path("weekdaySchedule");
    }
}