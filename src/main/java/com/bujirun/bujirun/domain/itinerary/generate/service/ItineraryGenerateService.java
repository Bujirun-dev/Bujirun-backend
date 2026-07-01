package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.itinerary.generate.client.GroqClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.global.util.GeoUtils;
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
    private final CollectionEntryRepository collectionEntryRepository;

    private static final List<Double> RADIUS_STEPS_M = List.of(15_000.0, 25_000.0, 40_000.0); // 15km → 25km → 40km
    private static final int MIN_CANDIDATES = 20; // 후보 장소 최소 개수

    public ItineraryGenerateResponse generateItinerary(SwipeRequest request, UUID userId) {

        // 스와이프 결과에서 contentId 목록 추출
        List<String> likedIds = request.getSwipes().stream()
                .filter(SwipeRequest.SwipeItem::isLiked)
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        List<String> dislikedIds = request.getSwipes().stream()
                .filter(s -> !s.isLiked())
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        // DB에서 좋아요한 관광지 조회 → 성향 벡터(카테고리별 선호도) 생성
        List<TourSpot> likedSpots = tourSpotRepository.findByContentIdIn(likedIds);
        Map<String, Long> preferenceVector = likedSpots.stream()
                .filter(s -> s.getCategory() != null)
                .collect(Collectors.groupingBy(TourSpot::getCategory, Collectors.counting()));

        // 선호 카테고리 기준으로 후보 관광지 조회 (최대 30개)
        List<String> preferredCategories = preferenceVector.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(3)
                .toList();

        // 좋아요한 곳들의 중심 좌표 계산 (좋아요 0개면 null → 거리 필터 스킵)
        Double centerLat = null;
        Double centerLng = null;
        if (!likedSpots.isEmpty()) {
            List<TourSpot> spotsWithCoord = likedSpots.stream()
                    .filter(s -> s.getLat() != null && s.getLng() != null)
                    .toList();
            if (!spotsWithCoord.isEmpty()) {
                centerLat = spotsWithCoord.stream().mapToDouble(s -> s.getLat().doubleValue()).average().orElseThrow();
                centerLng = spotsWithCoord.stream().mapToDouble(s -> s.getLng().doubleValue()).average().orElseThrow();
            }
        }

        List<TourSpot> filteredByCategory = tourSpotRepository
                .findByCategoryInOrderByName(preferredCategories)
                .stream()
                .filter(s -> !dislikedIds.contains(s.getContentId()))
                .filter(s -> !likedIds.contains(s.getContentId()))
                .toList();

        // 좋아요 중심 좌표 기준 거리 필터링 (반경 단계적으로 확대)
        List<TourSpot> categorySpots = filterByRadiusWithFallback(filteredByCategory, centerLat, centerLng)
                .stream()
                .limit(30 - likedSpots.size())
                .toList();

        // 좋아요한 곳 + 거리 필터링된 카테고리 후보 합치기
        Set<UUID> collectedSpotIds = collectionEntryRepository
                .findByUserIdAndCollectedTrue(userId)
                .stream()
                .map(ce -> ce.getSpot().getId())
                .collect(Collectors.toSet());

        List<TourSpot> allCandidates = new ArrayList<>(likedSpots);
        allCandidates.addAll(categorySpots);

        // 미수집 먼저, 수집 완료 나중 (둘 다 후보 포함)
        allCandidates.sort(Comparator.comparing(
                spot -> collectedSpotIds.contains(spot.getId()) ? 1 : 0
        ));

        // 여행 일수 계산
        long tripDays = request.getStartDate().until(request.getEndDate()).getDays() + 1;

        // 후보 관광지를 SpotInfo로 변환
        List<SpotInfo> candidates = allCandidates.stream()
                .map(this::toSpotInfo)
                .toList();

        List<SpotInfo> likedSpotInfos = likedSpots.stream()
                .map(this::toSpotInfo)
                .toList();

        // Groq 호출
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(likedSpotInfos, preferenceVector, candidates, tripDays, request.getOptimizationType());

        log.info("Groq 호출 시작 - 후보 관광지 {}개, 여행 {}일", candidates.size(), tripDays);
        String rawResponse = groqClient.chat(systemPrompt, userPrompt);
        log.info("Groq 응답 수신 완료");

        // JSON 파싱 → ScheduleResponse 변환
        return parseResponse(rawResponse, candidates, request.getOptimizationType());
    }

    private String buildSystemPrompt() {
        return """
                당신은 부산 여행 일정을 생성하는 전문가입니다.
                            반드시 아래 JSON 형식만 출력하세요. 설명이나 마크다운 없이 순수 JSON만 출력하세요.
                            필드명과 타입을 정확히 지키세요. "day"는 1부터 시작하는 정수이며, "date"가 아닙니다.
                            "spotContentIds"는 contentId 문자열 배열이며, "places" 같은 객체 배열이 아닙니다.
                
                {
                  "planA": {
                    "type": "A",
                    "label": "취향 집중 코스",
                    "description": "공통 취향을 가장 많이 반영한 일정",
                    "days": [...]
                  },
                  "planB": {
                    "type": "B",
                    "label": "뚜벅이 최적 코스",
                    "description": "이동 시간을 줄이고 효율적으로 즐기는 일정",
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

        sb.append("\n위 후보 관광지 중에서만 선택하여 A/B 2가지 일정을 생성하세요.");
        sb.append("\nA안은 선호 카테고리에 집중하고, 위 좋아요한 장소 목록에 있는 장소를 일정에 최대한 포함하세요.");
        sb.append("\nB안은 동선이 꼬이지 않도록 각 후보 관광지의 위도·경도를 기준으로 같은 권역(예: 수영구·해운대구, 중구·영도구 등 인접한 구/군)끼리 묶어서 묶음 단위로 하루 일정을 구성하세요. 서로 먼 권역의 관광지를 같은 날 또는 인접한 순서에 배치하지 마세요.");


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
                    .planA(parsePlan(root.get("planA"), spotMap, optimizationType, false)) // A안: 취향 집중 → Groq 추천 순서 그대로 유지
                    .planB(parsePlan(root.get("planB"), spotMap, optimizationType, true)) // B안: 뚜벅이 최적 → 좌표 기반 최근접 이웃으로 동선 재정렬
                    .planC(null)  // C안은 프론트에서 처리
                    .build();

        } catch (Exception e) {
            log.error("Groq 응답 파싱 실패: {}", rawResponse, e);
            throw new RuntimeException("일정 생성 중 오류가 발생했습니다.");
        }
    }

    private ItineraryGenerateResponse.PlanOption parsePlan(JsonNode planNode, Map<String, SpotInfo> spotMap, String optimizationType, boolean optimizeOrder) {
        if (planNode == null) return null;

        List<ItineraryGenerateResponse.DayPlan> days = new ArrayList<>();
        JsonNode daysNode = planNode.get("days");

        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode dayNode : daysNode) {
                JsonNode dayField = dayNode.get("day");
                if (dayField == null || !dayField.isInt()) {
                    log.warn("day 필드 누락 또는 형식 불일치, 해당 day 스킵: {}", dayNode);
                    continue;
                }
                int day = dayField.asInt();

                List<SpotInfo> spots = new ArrayList<>();
                JsonNode spotIds = dayNode.get("spotContentIds");
                if (spotIds != null && spotIds.isArray()) {
                    for (JsonNode idNode : spotIds) {
                        String contentId = idNode.isTextual() ? idNode.asText() : String.valueOf(idNode.asLong());
                        SpotInfo spot = spotMap.get(contentId);
                        if (spot != null) spots.add(spot);
                    }
                }

                if (optimizeOrder && spots.size() > 2) {
                    spots = sortByNearestNeighbor(spots);
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

    private List<SpotInfo> sortByNearestNeighbor(List<SpotInfo> spots) {
        List<SpotInfo> remaining = new ArrayList<>(spots);
        List<SpotInfo> sorted = new ArrayList<>();

        SpotInfo current = remaining.remove(0);
        sorted.add(current);

        while (!remaining.isEmpty()) {
            SpotInfo nearest = null;
            double minDist = Double.MAX_VALUE;

            for (SpotInfo candidate : remaining) {
                double dist = GeoUtils.haversineDistance(
                        current.getLat(), current.getLng(),
                        candidate.getLat(), candidate.getLng()
                );
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }

            sorted.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return sorted;
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

    private List<TourSpot> filterByRadiusWithFallback(List<TourSpot> spots, Double centerLat, Double centerLng) {
        if (centerLat == null) return spots; // 콜드 스타트(좋아요 0개 또는 좌표 없음) → 필터링 스킵

        for (double radius : RADIUS_STEPS_M) {
            List<TourSpot> filtered = spots.stream()
                    .filter(s -> isWithinRadius(s, centerLat, centerLng, radius))
                    .toList();
            if (filtered.size() >= MIN_CANDIDATES) {
                log.info("후보 반경 {}km 적용, {}개 확보", radius / 1000, filtered.size());
                return filtered;
            }
        }
        log.warn("반경을 최대로 넓혀도 후보 부족, 거리 필터링 없이 전체 후보 사용");
        return spots;
    }

    private boolean isWithinRadius(TourSpot spot, double centerLat, double centerLng, double radiusM) {
        if (spot.getLat() == null || spot.getLng() == null) return false;
        double dist = GeoUtils.haversineDistance(centerLat, centerLng,
                spot.getLat().doubleValue(), spot.getLng().doubleValue());
        return dist <= radiusM;
    }
}