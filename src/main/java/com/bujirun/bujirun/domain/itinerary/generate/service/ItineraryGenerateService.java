package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.itinerary.generate.client.OpenAiClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.swipe.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.global.util.GeoUtils;
import com.bujirun.bujirun.global.util.ScheduleCapacityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryGenerateService {

    private final OpenAiClient openAiClient;
    private final TourSpotRepository tourSpotRepository;
    private final ObjectMapper objectMapper;
    private final TransitRouteService transitRouteService;
    private final CollectionEntryRepository collectionEntryRepository;

    private static final List<Double> RADIUS_STEPS_M = List.of(15_000.0, 25_000.0, 40_000.0); // 15km → 25km → 40km
    private static final int MIN_CANDIDATES = 20; // 후보 장소 최소 개수

    @Transactional(readOnly = true)
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

        // OpenAI 호출
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(likedSpotInfos, preferenceVector, candidates, tripDays,
                request.getOptimizationType(), request.getStartDate(),
                request.getEndDate(), request.getStartTime(), request.getEndTime(),
                request.getActivityHours());

        log.info("OpenAI 호출 시작 - 후보 관광지 {}개, 여행 {}일", candidates.size(), tripDays);
        String rawResponse = openAiClient.chat(systemPrompt, userPrompt);
        log.info("OpenAI 응답 수신 완료");
        log.info("=== OPENAI RAW RESPONSE ===\n{}", rawResponse);

        // JSON 파싱 → ScheduleResponse 변환
        ItineraryGenerateResponse response = parseResponse(rawResponse, candidates, request.getOptimizationType());

        // OpenAI가 capacity보다 적게 채운 날짜 자동 백필
        backfillUnderfilledDays(response, allCandidates, likedSpots, preferenceVector,
                (int) tripDays, request.getStartTime(), request.getEndTime(),
                request.getActivityHours(), request.getOptimizationType());

        return response;
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
                                   String optimizationType,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   LocalTime startTime,
                                   LocalTime endTime,
                                   int activityHours) {
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

        sb.append("\n## 일차별 최대 관광지 수 (반드시 이 개수를 넘기지 마세요)\n");
        for (int day = 1; day <= tripDays; day++) {
            int maxSpots = ScheduleCapacityUtil.calculateMaxSpotsForDay(
                    day, (int) tripDays, startTime, endTime, activityHours);
            int hours = ScheduleCapacityUtil.calculateActivityHoursForDay(
                    day, (int) tripDays, startTime, endTime, activityHours);

            String note = "";
            if (day == 1 && startTime != null && tripDays > 1) {
                note = " (당일 " + startTime + " 도착이라 활동시간 " + hours + "시간뿐)";
            } else if (day == tripDays && endTime != null && tripDays > 1) {
                note = " (당일 " + endTime + "에 일정 종료라 활동시간 " + hours + "시간뿐)";
            }
            sb.append("- ").append(day).append("일차: 최대 ").append(maxSpots).append("곳").append(note).append("\n");
        }

        sb.append("\n## 후보 관광지 목록\n");
        candidates.forEach(spot ->
                sb.append("- contentId: ").append(spot.getContentId())
                        .append(", 이름: ").append(spot.getName())
                        .append(", 카테고리: ").append(spot.getCategory())
                        .append(", 지역: ").append(spot.getSigungu())
                        .append(", 운영시간: ").append(
                                spot.getOperatingHours() != null && !spot.getOperatingHours().isBlank()
                                        ? spot.getOperatingHours() : "정보없음")
                        .append(", 위치: (").append(spot.getLat()).append(", ").append(spot.getLng()).append(")\n")
        );

        sb.append("\n위 후보 관광지 중에서만 선택하여 A/B 2가지 일정을 생성하세요.");
        sb.append("\n각 일차별 최대 관광지 수는 위에 명시된 값을 절대 초과하지 마세요. 첫날/마지막날은 활동시간이 짧을 수 있으니 특히 유의하세요.");
        sb.append("\n중요: 각 일차는 운영시간상 불가능한 곳을 제외하면 반드시 명시된 최대 관광지 수만큼 채워야 합니다. 후보가 충분히 있는데도 임의로 1~2곳만 배정하지 마세요. 특정 날짜에 선호 카테고리 후보가 부족하면 다른 후보 관광지로 채워서라도 최대한 개수를 채우세요.");

        sb.append("\nA안은 선호 카테고리에 집중하고, 위 좋아요한 장소 목록에 있는 장소를 일정에 최대한 포함하세요.");
        sb.append("\nB안은 동선이 꼬이지 않도록 각 후보 관광지의 위도·경도를 기준으로 같은 권역(예: 수영구·해운대구, 중구·영도구 등 인접한 구/군)끼리 묶어서 묶음 단위로 하루 일정을 구성하세요. 서로 먼 권역의 관광지를 같은 날 또는 인접한 순서에 배치하지 마세요.");

        sb.append("\n\n## 운영시간 유의사항");
        sb.append("\n각 관광지의 '운영시간' 정보를 참고하여, 배정된 날짜(요일)·시간대에 실제로 운영하지 않는 곳(정기 휴무일, 계절 미운영 기간 등)은 해당 날짜의 일정에서 제외하세요.");
        sb.append("\n'상시 개방'이거나 운영시간 정보가 '정보없음'인 곳은 시간 제약 없이 포함해도 됩니다.");
        sb.append("\n하루 일정 내 관광지 방문 순서도 가능하면 각 관광지의 운영시간대 안에 들어오도록 배치하세요.");

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
                    .planA(parsePlan(root.get("planA"), spotMap, optimizationType, false)) // A안: 취향 집중 → OpenAI 추천 순서 그대로 유지
                    .planB(parsePlan(root.get("planB"), spotMap, optimizationType, true)) // B안: 뚜벅이 최적 → 좌표 기반 최근접 이웃으로 동선 재정렬
                    .planC(null)  // C안은 프론트에서 처리
                    .build();

        } catch (Exception e) {
            log.error("OpenAI 응답 파싱 실패: {}", rawResponse, e);
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

                log.info("day={}, spotIds raw={}, 파싱된 spots={}", day, spotIds, spots.size());

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

    /**
     * OpenAI가 특정 날짜에 capacity보다 적은 spot만 배정했을 경우,
     * 좋아요한 장소와 유사(카테고리 매칭 + 거리 근접)한 후보로 자동 백필한다.
     */
    private ItineraryGenerateResponse backfillUnderfilledDays(ItineraryGenerateResponse response,
                                                              List<TourSpot> allCandidates,
                                                              List<TourSpot> likedSpots,
                                                              Map<String, Long> preferenceVector,
                                                              int tripDays,
                                                              LocalTime startTime,
                                                              LocalTime endTime,
                                                              int activityHours,
                                                              String optimizationType) {
        ItineraryGenerateResponse.PlanOption newPlanA = backfillPlan(
                response.getPlanA(), allCandidates, likedSpots, preferenceVector,
                tripDays, startTime, endTime, activityHours, optimizationType);
        ItineraryGenerateResponse.PlanOption newPlanB = backfillPlan(
                response.getPlanB(), allCandidates, likedSpots, preferenceVector,
                tripDays, startTime, endTime, activityHours, optimizationType);

        return response.toBuilder()
                .planA(newPlanA)
                .planB(newPlanB)
                .build();
    }

    private ItineraryGenerateResponse.PlanOption backfillPlan(ItineraryGenerateResponse.PlanOption plan,
                                                              List<TourSpot> allCandidates,
                                                              List<TourSpot> likedSpots,
                                                              Map<String, Long> preferenceVector,
                                                              int tripDays,
                                                              LocalTime startTime,
                                                              LocalTime endTime,
                                                              int activityHours,
                                                              String optimizationType) {
        if (plan == null || plan.getDays() == null) return plan;

        // 이 플랜 전체에서 이미 사용된 contentId (같은 관광지 중복 배정 방지)
        Set<String> usedInPlan = plan.getDays().stream()
                .flatMap(d -> d.getSpots().stream())
                .map(SpotInfo::getContentId)
                .collect(Collectors.toCollection(HashSet::new));

        List<ItineraryGenerateResponse.DayPlan> newDays = new ArrayList<>();

        for (ItineraryGenerateResponse.DayPlan dayPlan : plan.getDays()) {
            int capacity = ScheduleCapacityUtil.calculateMaxSpotsForDay(
                    dayPlan.getDay(), tripDays, startTime, endTime, activityHours);

            List<SpotInfo> spots = new ArrayList<>(dayPlan.getSpots());

            if (spots.size() < capacity) {
                // 이 날짜의 기준 좌표: 이미 배정된 spot들의 중심, 없으면 좋아요 장소 중심
                double refLat, refLng;
                if (!spots.isEmpty()) {
                    refLat = spots.stream().mapToDouble(SpotInfo::getLat).average().orElse(0);
                    refLng = spots.stream().mapToDouble(SpotInfo::getLng).average().orElse(0);
                } else if (!likedSpots.isEmpty()) {
                    refLat = likedSpots.stream()
                            .filter(s -> s.getLat() != null).mapToDouble(s -> s.getLat().doubleValue()).average().orElse(0);
                    refLng = likedSpots.stream()
                            .filter(s -> s.getLng() != null).mapToDouble(s -> s.getLng().doubleValue()).average().orElse(0);
                } else {
                    refLat = 0;
                    refLng = 0;
                }

                final double fRefLat = refLat;
                final double fRefLng = refLng;

                List<TourSpot> fillCandidates = allCandidates.stream()
                        .filter(s -> !usedInPlan.contains(s.getContentId()))
                        .filter(s -> s.getLat() != null && s.getLng() != null)
                        .sorted(Comparator
                                // 선호 카테고리 점수 높은 순
                                .comparingLong((TourSpot s) -> -preferenceVector.getOrDefault(s.getCategory(), 0L))
                                // 그 다음 기준 좌표와 가까운 순
                                .thenComparingDouble(s -> GeoUtils.haversineDistance(
                                        fRefLat, fRefLng, s.getLat().doubleValue(), s.getLng().doubleValue())))
                        .toList();

                for (TourSpot candidate : fillCandidates) {
                    if (spots.size() >= capacity) break;
                    spots.add(toSpotInfo(candidate));
                    usedInPlan.add(candidate.getContentId());
                }

                if (spots.size() < dayPlan.getSpots().size() + 1) {
                    log.info("day={} 백필 시도했으나 후보 부족 (기존 {}개 → {}개, capacity {}개)",
                            dayPlan.getDay(), dayPlan.getSpots().size(), spots.size(), capacity);
                } else {
                    log.info("day={} 백필 완료: {}개 → {}개 (capacity {}개)",
                            dayPlan.getDay(), dayPlan.getSpots().size(), spots.size(), capacity);
                }
            }

            newDays.add(dayPlan.toBuilder()
                    .spots(spots)
                    .routes(transitRouteService.getRoutesForDay(spots, optimizationType))
                    .build());
        }

        return plan.toBuilder()
                .days(newDays)
                .build();
    }
}