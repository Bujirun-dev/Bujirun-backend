package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.client.GroqClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.SwipeRequest;
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
public class ItineraryGenerateService {

    private final GroqClient groqClient;
    private final TourSpotRepository tourSpotRepository;
    private final ObjectMapper objectMapper;
    private final TransitRouteService transitRouteService;

    public ItineraryGenerateResponse generateItinerary(SwipeRequest request) {

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

        List<TourSpot> categorySpots = tourSpotRepository
                .findByCategoryInOrderByName(preferredCategories)
                .stream()
                .filter(s -> !dislikedIds.contains(s.getContentId()))
                .filter(s -> !likedIds.contains(s.getContentId()))
                .limit(30 - likedSpots.size())
                .toList();

        List<TourSpot> allCandidates = new ArrayList<>(likedSpots);
        allCandidates.addAll(categorySpots);

        // 4. 여행 일수 계산
        long tripDays = request.getStartDate().until(request.getEndDate()).getDays() + 1;

        // 5. 후보 관광지를 SpotInfo로 변환
        List<SpotInfo> candidates = allCandidates.stream()
                .map(this::toSpotInfo)
                .toList();

        List<SpotInfo> likedSpotInfos = likedSpots.stream()
                .map(this::toSpotInfo)
                .toList();

        // 6. Groq 호출
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(likedSpotInfos, preferenceVector, candidates, tripDays, request.getOptimizationType());

        log.info("Groq 호출 시작 - 후보 관광지 {}개, 여행 {}일", candidates.size(), tripDays);
        String rawResponse = groqClient.chat(systemPrompt, userPrompt);
        log.info("Groq 응답 수신 완료");

        // 7. JSON 파싱 → ScheduleResponse 변환
        return parseResponse(rawResponse, candidates, request.getOptimizationType());
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

    private String buildUserPrompt(List<SpotInfo> likedSpots,
                                   Map<String, Long> preferenceVector,
                                   List<SpotInfo> candidates,
                                   long tripDays,
                                   String optimizationType) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 좋아요한 장소 목록\n");
        likedSpots.forEach(spot ->
                sb.append("- 이름: ").append(spot.getName())
                        .append(", 카테고리: ").append(spot.getCategory())
                        .append(", 지역: ").append(spot.getSigungu()).append("\n"));

        sb.append("\n## 사용자 성향 벡터 (카테고리별 선호도)\n");
        preferenceVector.forEach((category, count) ->
                sb.append("- ").append(category).append(": ").append(count).append("회 좋아요\n"));

        sb.append("\n## 이동 최적화 기준: ").append(
                switch (optimizationType != null ? optimizationType : "TIME_SHORT") {
                    case "WALK_MIN" -> "도보 최소화";
                    case "COST_SAVE" -> "비용 절약";
                    case "TRANSFER_MIN" -> "환승 최소화";
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
        sb.append("\nA안은 선호 카테고리에 집중하고, 위 좋아요한 장소 목록에 있는 장소를 일정에 최대한 포함하세요.");
        sb.append("\nB안은 동선이 꼬이지 않도록 각 후보 관광지의 위도·경도를 기준으로 같은 권역(예: 수영구·해운대구, 중구·영도구 등 인접한 구/군)끼리 묶어서 묶음 단위로 하루 일정을 구성하세요. 서로 먼 권역의 관광지를 같은 날 또는 인접한 순서에 배치하지 마세요.");
        sb.append("\nC안은 다양한 카테고리 포함.");
        sb.append("\nA안은 선호 카테고리에 집중하고, 위 좋아요한 장소 목록에 있는 장소를 가능한 한 많이 포함하세요.");

        return sb.toString();
    }

    private ItineraryGenerateResponse parseResponse(String rawResponse, List<SpotInfo> candidates, String optimizationType) {
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

            return ItineraryGenerateResponse.builder()
                    .planA(parsePlan(root.get("planA"), spotMap, optimizationType))
                    .planB(parsePlan(root.get("planB"), spotMap, optimizationType))
                    .planC(parsePlan(root.get("planC"), spotMap, optimizationType))
                    .build();

        } catch (Exception e) {
            log.error("Groq 응답 파싱 실패: {}", rawResponse, e);
            throw new RuntimeException("일정 생성 중 오류가 발생했습니다.");
        }
    }

    private ItineraryGenerateResponse.PlanOption parsePlan(JsonNode planNode, Map<String, SpotInfo> spotMap, String optimizationType) {
        if (planNode == null) return null;

        List<ItineraryGenerateResponse.DayPlan> days = new ArrayList<>();
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

                days.add(ItineraryGenerateResponse.DayPlan.builder()
                        .day(day)
                        .spots(spots)
                        .routes(transitRouteService.getRoutesForDay(spots, optimizationType))
                        .build());
            }
        }

        return ItineraryGenerateResponse.PlanOption.builder()
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