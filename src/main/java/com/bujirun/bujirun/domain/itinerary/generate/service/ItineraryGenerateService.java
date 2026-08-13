package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary; // 추가: 그룹 추천 이유 생성용
import com.bujirun.bujirun.domain.itinerary.generate.client.OpenAiClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.exception.InvalidItineraryRequestException;
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
    private static final int MAX_TRIP_DAYS = 4; // 최대 3박 4일
    private static final int MIN_ACTIVITY_HOURS = 1; // 하루 최소 활동시간
    private static final int MAX_ACTIVITY_HOURS = 16; // 하루 최대 활동시간 상한
    private static final int DEFAULT_ACTIVITY_HOURS = 12; // 추가: activityHours 미입력 시 기본값 (09:00~21:00 기준)
    private static final int MIN_PLAN_DIFF_SPOTS = 3; // 추가: 그룹 일정 생성 시 planA/planB 최소 차별화 스팟 수 (다양성 규칙)

    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generateItinerary(SwipeRequest request, UUID userId) {
        return generateItinerary(request, userId, null);
    }

    // 추가: 그룹 일정 생성 시 취향 집계(GroupPreferenceSummary)를 전달받아 추천 이유를 함께 생성한다. null이면 기존 개인 일정 로직과 완전히 동일하게 동작한다.
    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generateItinerary(SwipeRequest request, UUID userId, GroupPreferenceSummary groupPreferenceSummary) {

        // 여행 일수 계산 및 상한 검증 (최대 3박 4일)
        long tripDays = request.getStartDate().until(request.getEndDate()).getDays() + 1;
        validateTripDuration(tripDays, request.getStartDate(), request.getEndDate());

        // activityHours 없으면 기본값/계산값으로 대체
        int activityHours = resolveActivityHours(request);
        validateActivityTime(request.getStartDate(), request.getEndDate(),
                request.getStartTime(), request.getEndTime(), activityHours);

        // 스와이프 결과에서 contentId 목록 추출 (swipes가 null이면 빈 리스트로 대체)
        List<SwipeRequest.SwipeItem> swipes = request.getSwipes() != null
                ? request.getSwipes()
                : List.of();

        List<String> likedIds = swipes.stream()
                .filter(SwipeRequest.SwipeItem::isLiked)
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        List<String> dislikedIds = swipes.stream()
                .filter(s -> !s.isLiked())
                .map(SwipeRequest.SwipeItem::getContentId)
                .toList();

        // DB에서 좋아요한 관광지 조회 → 성향 벡터(카테고리별 선호도) 생성
        List<TourSpot> likedSpots = tourSpotRepository.findByContentIdIn(likedIds);
        if (likedSpots.size() < likedIds.size()) {
            Set<String> foundIds = likedSpots.stream().map(TourSpot::getContentId).collect(Collectors.toSet());
            List<String> missingIds = likedIds.stream().filter(id -> !foundIds.contains(id)).toList();
            log.warn("요청된 likedIds 중 DB에 없는 contentId 존재: {}", missingIds);
        }

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

        List<TourSpot> filteredByCategory;
        if (preferredCategories.isEmpty()) {
            filteredByCategory = getBalancedColdStartCandidates(dislikedIds, likedIds);
            log.info("콜드스타트(취향 데이터 없음) - 구군 분산 후보 {}개", filteredByCategory.size());
        } else {
            filteredByCategory = tourSpotRepository
                    .findByCategoryInOrderByName(preferredCategories)
                    .stream()
                    .filter(s -> !dislikedIds.contains(s.getContentId()))
                    .filter(s -> !likedIds.contains(s.getContentId()))
                    .toList();
        }

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

        // 후보 관광지를 SpotInfo로 변환
        List<SpotInfo> candidates = allCandidates.stream()
                .map(this::toSpotInfo)
                .toList();

        List<SpotInfo> likedSpotInfos = likedSpots.stream()
                .map(this::toSpotInfo)
                .toList();

        // OpenAI 호출
        String systemPrompt = buildSystemPrompt(groupPreferenceSummary); // 그룹 일정이면 추천 이유 스키마 + 다양성 규칙 포함
        String userPrompt = buildUserPrompt(likedSpotInfos, preferenceVector, candidates, tripDays,
                request.getOptimizationType(), request.getStartDate(),
                request.getEndDate(), request.getStartTime(), request.getEndTime(), activityHours,
                groupPreferenceSummary);

        log.info("OpenAI 호출 시작 - 후보 관광지 {}개, 여행 {}일", candidates.size(), tripDays);
        String rawResponse = openAiClient.chat(systemPrompt, userPrompt);
        log.info("OpenAI 응답 수신 완료");
        log.info("=== OPENAI RAW RESPONSE ===\n{}", rawResponse);

        // JSON 파싱 → ScheduleResponse 변환
        ItineraryGenerateResponse response = parseResponse(rawResponse, candidates, request.getOptimizationType());

        // OpenAI가 capacity보다 적게 채운 날짜 자동 백필
        backfillUnderfilledDays(response, allCandidates, likedSpots, preferenceVector,
                (int) tripDays, request.getStartTime(), request.getEndTime(),
                activityHours, request.getOptimizationType());

        return response;
    }

    // 활동시간 계산
    private int resolveActivityHours(SwipeRequest request) {
        if (request.getActivityHours() != null && request.getActivityHours() > 0) {
            return request.getActivityHours();
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getStartDate().equals(request.getEndDate())) {
            long minutes = java.time.Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            return (int) Math.max(MIN_ACTIVITY_HOURS, minutes / 60);
        }
        return DEFAULT_ACTIVITY_HOURS;
    }

    // 여행기간 검증
    private void validateTripDuration(long tripDays, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidItineraryRequestException(
                    "종료일이 시작일보다 빠를 수 없습니다. startDate=" + startDate + ", endDate=" + endDate);
        }
        if (tripDays > MAX_TRIP_DAYS) {
            throw new InvalidItineraryRequestException(
                    "여행 기간은 최대 " + MAX_TRIP_DAYS + "일(3박4일)까지 지원합니다. 요청된 기간: " + tripDays + "일");
        }
        if (tripDays < 1) {
            throw new InvalidItineraryRequestException(
                    "여행 기간은 최소 1일 이상이어야 합니다. 요청된 기간: " + tripDays + "일");
        }
    }

    // 여행시간 검증
    private void validateActivityTime(LocalDate startDate, LocalDate endDate, LocalTime startTime, LocalTime endTime, int activityHours) {
        if (activityHours < MIN_ACTIVITY_HOURS || activityHours > MAX_ACTIVITY_HOURS) {
            throw new InvalidItineraryRequestException(
                    "activityHours는 " + MIN_ACTIVITY_HOURS + "~" + MAX_ACTIVITY_HOURS + " 사이여야 합니다. 요청값: " + activityHours);
        }

        // 같은 날짜일 때만 시각 순서 검증
        if (startDate.equals(endDate) && startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new InvalidItineraryRequestException(
                    "종료 시간은 시작 시간보다 늦어야 합니다. startTime=" + startTime + ", endTime=" + endTime);
        }
    }

    // groupPreferenceSummary가 null이면 개인 일정 프롬프트(기존 로직 그대로), 아니면 그룹 전용 프롬프트를 반환한다
    private String buildSystemPrompt(GroupPreferenceSummary groupPreferenceSummary) {
        if (groupPreferenceSummary == null) {
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

        // 그룹 일정 생성 전용 — summaryReason(플랜별 한 줄 추천 이유), spotReasons(스팟별 추천 이유 배열) 필드를 요구한다.
        String base = """
                당신은 부산 여행 일정을 생성하는 전문가입니다.
                            반드시 아래 JSON 형식만 출력하세요. 설명이나 마크다운 없이 순수 JSON만 출력하세요.
                            필드명과 타입을 정확히 지키세요. "day"는 1부터 시작하는 정수이며, "date"가 아닙니다.
                            "spotContentIds"는 contentId 문자열 배열이며, "places" 같은 객체 배열이 아닙니다.
                            "summaryReason"은 해당 플랜을 추천하는 이유를 한 줄로 요약한 문자열입니다.
                            "spotReasons"는 { "contentId": ["이유1", "이유2"] } 형태의 객체이며, spotContentIds에 포함된 각 관광지를 일정에 포함한 이유를 3개 내외의 문자열 배열로 담습니다.

                {
                  "planA": {
                    "type": "A",
                    "label": "취향 집중 코스",
                    "description": "공통 취향을 가장 많이 반영한 일정",
                    "summaryReason": "그룹원들이 가장 많이 좋아요한 카테고리를 집중 반영한 코스입니다",
                    "days": [
                      {
                        "day": 1,
                        "spotContentIds": ["..."],
                        "spotReasons": { "126508": ["그룹원 4명이 좋아요한 장소", "선호 카테고리와 일치"] }
                      }
                    ]
                  },
                  "planB": {
                    "type": "B",
                    "label": "뚜벅이 최적 코스",
                    "description": "이동 시간을 줄이고 효율적으로 즐기는 일정",
                    "summaryReason": "...",
                    "days": [...]
                  }
                }
                """;

        // planA/planB가 거의 동일한 스팟으로 채워지지 않도록 명시적 다양성 규칙 부여
        String diversityRule = "\n## 그룹 일정 다양성 규칙\n"
                + "planA와 planB는 스팟 구성이 최소 " + MIN_PLAN_DIFF_SPOTS + "곳 이상 서로 달라야 합니다. "
                + "두 플랜을 동일하거나 거의 동일한 관광지 목록으로 채우지 마세요.\n";

        // isUniformPreference 여부에 따라 summaryReason/spotReasons 작성 지침을 분기한다
        String reasonGuidance = groupPreferenceSummary.isUniformPreference()
                ? "\n## 추천 이유 작성 지침 (편중 없는 취향)\n"
                        + "그룹원들의 선호가 특정 카테고리에 뚜렷하게 쏠리지 않고 폭넓게 분산되어 있습니다. "
                        + "summaryReason과 spotReasons에서 특정 카테고리를 근거로 들지 말고, "
                        + "\"그룹원들이 폭넓게 선호를 표시해 대표 명소 위주로 구성했습니다\"와 같이 일반화된 이유를 사용하세요.\n"
                : "\n## 추천 이유 작성 지침 (뚜렷한 취향)\n"
                        + "그룹원들의 선호가 특정 카테고리에 뚜렷하게 쏠려 있습니다. "
                        + "summaryReason과 spotReasons에서는 유저 프롬프트에 제공되는 그룹원 취향 집계(categoryScore) 상위 카테고리를 "
                        + "구체적인 근거로 사용하세요.\n";

        return base + diversityRule + reasonGuidance;
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
                                   int activityHours,
                                   GroupPreferenceSummary groupPreferenceSummary) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 좋아요한 장소 목록\n");
        likedSpots.forEach(spot ->
                sb.append("- 이름: ").append(spot.getName())
                        .append(", 카테고리: ").append(spot.getCategory())
                        .append(", 지역: ").append(spot.getSigungu()).append("\n"));

        sb.append("\n## 사용자 성향 벡터 (카테고리별 선호도)\n");
        preferenceVector.forEach((category, count) ->
                sb.append("- ").append(category).append(": ").append(count).append("회 좋아요\n"));

        // 그룹 일정 생성 시에만 그룹원 취향 집계 컨텍스트와 reason 작성 지침을 포함한다
        if (groupPreferenceSummary != null) {
            sb.append("\n## 그룹원 취향 집계 (스와이프 완료 ").append(groupPreferenceSummary.getParticipantCount()).append("명)\n");
            groupPreferenceSummary.getCategoryCounts().forEach((category, count) ->
                    sb.append("- ").append(category).append(": ").append(count).append("명 좋아요\n"));

            // selectivity 가중치를 반영한 categoryScore — 단순 좋아요 수보다, 취향이 뚜렷한 참여자의 의견에 더 큰 비중을 둔 지표
            sb.append("\n## 그룹원 취향 집계 (selectivity 가중치 반영 점수, 근거로 우선 사용)\n");
            groupPreferenceSummary.getCategoryScore().forEach((category, score) ->
                    sb.append("- ").append(category).append(": ").append(String.format("%.2f", score)).append("점\n"));

            sb.append("\n## 편중도(isUniformPreference): ").append(groupPreferenceSummary.isUniformPreference()).append("\n");
            sb.append("\n위 그룹원 취향 집계를 근거로 각 플랜의 summaryReason과 각 관광지의 spotReasons를 구체적으로 작성하세요. ")
                    .append("isUniformPreference와 categoryScore에 따른 작성 지침은 시스템 프롬프트를 따르세요.");
        }

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
        Set<String> seenInPlan = new HashSet<>();  // 플랜 전체 중복 방지

        if (daysNode != null && daysNode.isArray()) {
            for (JsonNode dayNode : daysNode) {
                JsonNode dayField = dayNode.get("day");
                if (dayField == null || !dayField.isInt()) {
                    log.warn("day 필드 누락 또는 형식 불일치, 해당 day 스킵: {}", dayNode);
                    continue;
                }
                int day = dayField.asInt();
                JsonNode spotReasonsNode = dayNode.get("spotReasons"); // 추가: 그룹 일정 생성 시에만 존재하는 스팟별 추천 이유

                List<SpotInfo> spots = new ArrayList<>();
                JsonNode spotIds = dayNode.get("spotContentIds");
                if (spotIds != null && spotIds.isArray()) {
                    for (JsonNode idNode : spotIds) {
                        String contentId = idNode.isTextual() ? idNode.asText() : String.valueOf(idNode.asLong());
                        if (!seenInPlan.add(contentId)) {
                            log.warn("day={}에서 이미 다른 날에 배정된 contentId={} 중복 스킵", day, contentId);
                            continue;
                        }
                        SpotInfo spot = spotMap.get(contentId);
                        if (spot != null) {
                            // spotReasons가 있으면 이 스팟에만 이유를 채워서 새 인스턴스로 추가 (spotMap 공유 인스턴스는 변경하지 않음)
                            List<String> reasons = parseSpotReasons(spotReasonsNode, contentId);
                            spots.add(reasons != null ? spot.toBuilder().reasons(reasons).build() : spot);
                        } else {
                            log.warn("day={}, contentId={}가 candidates 목록에 없음 (OpenAI 응답 오류 가능성)", day, contentId);
                        }
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
                .summaryReason(planNode.hasNonNull("summaryReason") ? planNode.get("summaryReason").asText() : null) // 추가: 그룹 일정 생성 시에만 존재
                .days(days)
                .build();
    }

    // spotReasons({ contentId: [이유, ...] } 형태의 JSON 객체)에서 특정 contentId의 이유 배열을 defensive하게 추출
    private List<String> parseSpotReasons(JsonNode spotReasonsNode, String contentId) {
        if (spotReasonsNode == null || !spotReasonsNode.isObject()) return null;
        JsonNode reasonsArr = spotReasonsNode.get(contentId);
        if (reasonsArr == null || !reasonsArr.isArray()) return null;

        List<String> reasons = new ArrayList<>();
        for (JsonNode r : reasonsArr) {
            if (r.isTextual()) {
                reasons.add(r.asText());
            }
        }
        return reasons.isEmpty() ? null : reasons;
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

    /**
     * 콜드스타트(좋아요 데이터 없음) 시 구군별로 고르게 분산된 후보를 뽑는다.
     * 특정 구군 편중을 막기 위해 구군 단위 라운드로빈으로 채운다.
     */
    private List<TourSpot> getBalancedColdStartCandidates(List<String> dislikedIds, List<String> likedIds) {
        List<TourSpot> all = tourSpotRepository.findAll().stream()
                .filter(s -> !dislikedIds.contains(s.getContentId()))
                .filter(s -> !likedIds.contains(s.getContentId()))
                .filter(s -> s.getSigungu() != null)
                .toList();

        Map<Integer, List<TourSpot>> bySigungu = all.stream()
                .collect(Collectors.groupingBy(s -> s.getSigungu().getId()));

        bySigungu.values().forEach(Collections::shuffle);

        List<TourSpot> balanced = new ArrayList<>();
        boolean added;
        do {
            added = false;
            for (List<TourSpot> spots : bySigungu.values()) {
                if (!spots.isEmpty()) {
                    balanced.add(spots.remove(0));
                    added = true;
                }
            }
        } while (added && balanced.size() < 30);

        return balanced;
    }
}