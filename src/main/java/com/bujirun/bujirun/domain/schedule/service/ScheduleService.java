package com.bujirun.bujirun.domain.schedule.service;

import com.bujirun.bujirun.domain.schedule.client.GroqClient;
import com.bujirun.bujirun.domain.schedule.dto.ScheduleResponse;
import com.bujirun.bujirun.domain.schedule.dto.SpotInfo;
import com.bujirun.bujirun.domain.schedule.dto.SwipeRequest;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final GroqClient groqClient;
    private final TourSpotRepository tourSpotRepository;
    private final ObjectMapper objectMapper;

    public ScheduleResponse generateSchedule(SwipeRequest request) {

        // 1. 스와이프 결과에서 contentId 목록 추출
        List<String> likedIds = request.getSwipes().stream()
                .filter(SwipeRequest.SwipeItem::isLiked)
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        List<String> dislikedIds = request.getSwipes().stream()
                .filter(s -> !s.isLiked())
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        // 2. DB에서 좋아요한 관광지 조회 → 성향 벡터(카테고리별 선호도) 생성
        List<TourSpot> likedSpots = tourSpotRepository.findByContentIdIn(likedIds);
        Map<String, Long> preferenceVector = likedSpots.stream()
                .filter(s -> s.getCategory() != null)
                .collect(Collectors.groupingBy(TourSpot::getCategory, Collectors.counting()));

        // 3. 선호 카테고리 기준으로 후보 관광지 조회 (최대 30개)
        List<String> preferredCategories = preferenceVector.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();

        List<TourSpot> candidateSpots = tourSpotRepository
                .findByCategoryInOrderByName(preferredCategories)
                .stream()
                .filter(s -> !dislikedIds.contains(s.getContentId()))
                .limit(30)
                .toList();

        // 4. 여행 일수 계산
        long tripDays = request.getStartDate().until(request.getEndDate()).getDays() + 1;

        // 5. 후보 관광지를 SpotInfo로 변환
        List<SpotInfo> candidates = candidateSpots.stream()
                .map(this::toSpotInfo)
                .toList();

        // 6. Groq 호출
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(preferenceVector, candidates, tripDays, request.getOptimizationType());

        log.info("Groq 호출 시작 - 후보 관광지 {}개, 여행 {}일", candidates.size(), tripDays);
        String rawResponse = groqClient.chat(systemPrompt, userPrompt);
        log.info("Groq 응답 수신 완료");

        // 7. JSON 파싱 → ScheduleResponse 변환
        return parseResponse(rawResponse, candidates);
    }

    private String buildSystemPrompt() {
        return """
                당신은 부산 여행 일정을 생성하는 전문가입니다.
                반드시 아래 JSON 형식만 출력하세요. 설명이나 마크다운 없이 순수 JSON만 출력하세요.
                
                {
                  "planA": {
                    "type": "A",
                    "label": "취향 집중형",
                    "description": "선호 태그에 집중한 일정",
                    "days": [
                      {
                        "day": 1,
                        "spotContentIds": ["contentId1", "contentId2", "contentId3"]
                      }
                    ]
                  },
                  "planB": {
                    "type": "B",
                    "label": "균형 최적형",
                    "description": "이동 효율을 우선한 일정",
                    "days": [...]
                  },
                  "planC": {
                    "type": "C",
                    "label": "자유 편집형",
                    "description": "다양한 카테고리를 포함한 일정",
                    "days": [...]
                  }
                }
                """;
    }

    private String buildUserPrompt(Map<String, Long> preferenceVector,
                                   List<SpotInfo> candidates,
                                   long tripDays,
                                   String optimizationType) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 사용자 성향 벡터 (카테고리별 선호도)\n");
        preferenceVector.forEach((category, count) ->
                sb.append("- ").append(category).append(": ").append(count).append("회 좋아요\n"));

        sb.append("\n## 이동 최적화 기준: ").append(
                switch (optimizationType != null ? optimizationType : "TIME_SHORT") {
                    case "WALK_MIN" -> "도보 최소화";
                    case "COST_SAVE" -> "비용 절약";
                    default -> "시간 단축";
                }
        ).append("\n");

        sb.append("\n## 여행 일수: ").append(tripDays).append("일\n");
        sb.append("## 하루 최대 관광지 수: 4곳\n");

        sb.append("\n## 후보 관광지 목록\n");
        candidates.forEach(spot ->
                sb.append("- contentId: ").append(spot.getContentId())
                        .append(", 이름: ").append(spot.getName())
                        .append(", 카테고리: ").append(spot.getCategory())
                        .append(", 지역: ").append(spot.getSigungu())
                        .append(", 위치: (").append(spot.getLat()).append(", ").append(spot.getLng()).append(")\n")
        );

        sb.append("\n위 후보 관광지 중에서만 선택하여 A/B/C 3가지 일정을 생성하세요.");
        sb.append("\nA안은 선호 카테고리 집중, B안은 지리적으로 가까운 관광지 묶기, C안은 다양한 카테고리 포함.");

        return sb.toString();
    }

    private ScheduleResponse parseResponse(String rawResponse, List<SpotInfo> candidates) {
        try {
            // JSON 앞뒤 불필요한 텍스트 제거
            String json = rawResponse.trim();
            if (json.contains("```")) {
                json = json.replaceAll("```json", "").replaceAll("```", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);

            // contentId → SpotInfo 맵
            Map<String, SpotInfo> spotMap = candidates.stream()
                    .collect(Collectors.toMap(SpotInfo::getContentId, s -> s));

            return ScheduleResponse.builder()
                    .planA(parsePlan(root.get("planA"), spotMap))
                    .planB(parsePlan(root.get("planB"), spotMap))
                    .planC(parsePlan(root.get("planC"), spotMap))
                    .build();

        } catch (Exception e) {
            log.error("Groq 응답 파싱 실패: {}", rawResponse, e);
            throw new RuntimeException("일정 생성 중 오류가 발생했습니다.");
        }
    }

    private ScheduleResponse.PlanOption parsePlan(JsonNode planNode, Map<String, SpotInfo> spotMap) {
        if (planNode == null) return null;

        List<ScheduleResponse.DayPlan> days = new ArrayList<>();
        JsonNode daysNode = planNode.get("days");

        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode dayNode : daysNode) {
                int day = dayNode.get("day").asInt();
                List<SpotInfo> spots = new ArrayList<>();

                JsonNode spotIds = dayNode.get("spotContentIds");
                if (spotIds != null && spotIds.isArray()) {
                    for (JsonNode idNode : spotIds) {
                        String contentId = idNode.asText();
                        SpotInfo spot = spotMap.get(contentId);
                        if (spot != null) spots.add(spot);
                    }
                }

                days.add(ScheduleResponse.DayPlan.builder()
                        .day(day)
                        .spots(spots)
                        .build());
            }
        }

        return ScheduleResponse.PlanOption.builder()
                .type(planNode.path("type").asText())
                .label(planNode.path("label").asText())
                .description(planNode.path("description").asText())
                .days(days)
                .build();
    }

    private SpotInfo toSpotInfo(TourSpot spot) {
        return SpotInfo.builder()
                .contentId(spot.getContentId())
                .name(spot.getName())
                .category(spot.getCategory())
                .sigungu(spot.getSigungu() != null ? spot.getSigungu().getName() : null)
                .lat(spot.getLat() != null ? spot.getLat().doubleValue() : 0)
                .lng(spot.getLng() != null ? spot.getLng().doubleValue() : 0)
                .address(spot.getAddress())
                .thumbnailUrl(spot.getThumbnailUrl())
                .operatingHours(spot.getOperatingHours())
                .build();
    }
}