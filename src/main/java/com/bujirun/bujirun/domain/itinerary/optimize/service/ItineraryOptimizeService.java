package com.bujirun.bujirun.domain.itinerary.optimize.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.client.GroqClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import com.bujirun.bujirun.domain.itinerary.generate.service.SpotOrderOptimizer;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.request.ItineraryOptimizeRequest;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.response.ItineraryOptimizeResponse;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryDayRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryOptimizeService {

    private final ItineraryDayRepository itineraryDayRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TransitRouteService transitRouteService;
    private final GroqClient groqClient;
    private final ObjectMapper objectMapper;

    @Value("${itinerary.default-visit-duration-minutes:60}")
    private int defaultVisitDurationMinutes;

    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);

    public ItineraryOptimizeResponse optimizeDay(UUID dayId, ItineraryOptimizeRequest request, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. dayId=" + dayId));

        validateAccess(day.getItinerary(), userId);

        List<ItineraryItem> items = day.getItems();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("재최적화할 관광지가 없습니다.");
        }

        List<SpotInfo> spots = items.stream()
                .map(this::toSpotInfo)
                .toList();

        LocalTime startTime = request.getStartTime() != null
                ? request.getStartTime()
                : (items.get(0).getArrivalTime() != null ? items.get(0).getArrivalTime() : DEFAULT_START_TIME);

        // 1차: 좌표 기반 nearest-neighbor 재정렬
        List<SpotInfo> baseOrder = SpotOrderOptimizer.sortByNearestNeighbor(spots);

        // 2차: 운영시간 고려해서 Groq한테 최종 순서 조정 요청
        List<SpotInfo> finalOrder = baseOrder;
        String reason = "이동 거리를 기준으로 동선을 최적화했어요.";

        boolean hasOperatingHours = baseOrder.stream()
                .anyMatch(s -> s.getOperatingHours() != null && !s.getOperatingHours().isBlank());

        if (hasOperatingHours) {
            List<Integer> travelTimes = calculateTravelTimes(baseOrder, request.getOptimizationType());
            List<LocalTime> arrivalTimes = calculateArrivalTimes(startTime, travelTimes);

            try {
                GroqAdjustResult adjusted = adjustWithGroq(baseOrder, arrivalTimes);
                if (adjusted != null && !adjusted.order().isEmpty()) {
                    finalOrder = adjusted.order();
                    reason = adjusted.reason();
                }
            } catch (Exception e) {
                log.warn("Groq 운영시간 조정 실패, nearest-neighbor 순서 그대로 사용: {}", e.getMessage());
            }
        }

        // 최종 순서로 구간 경로 + 도착시각 재계산
        List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(finalOrder, request.getOptimizationType());
        List<Integer> finalTravelTimes = extractTravelTimes(routes);
        List<LocalTime> finalArrivalTimes = calculateArrivalTimes(startTime, finalTravelTimes);

        // 결과를 ItineraryItem에 반영
        applyToEntities(items, finalOrder, finalArrivalTimes);

        List<ItineraryOptimizeResponse.OptimizedSpot> optimizedSpots = new ArrayList<>();
        for (int i = 0; i < finalOrder.size(); i++) {
            SpotInfo spot = finalOrder.get(i);
            optimizedSpots.add(ItineraryOptimizeResponse.OptimizedSpot.builder()
                    .contentId(spot.getContentId())
                    .name(spot.getName())
                    .order(i + 1)
                    .arrivalTime(finalArrivalTimes.get(i))
                    .build());
        }

        return ItineraryOptimizeResponse.builder()
                .spots(optimizedSpots)
                .routes(routes)
                .reason(reason)
                .build();
    }

    // 소유자 또는 그룹원이면 접근 허용 (ItineraryService와 동일 정책)
    private void validateAccess(Itinerary itinerary, UUID userId) {
        if (itinerary.getUserId().equals(userId)) return;
        if (itinerary.getGroupId() != null
                && groupMemberRepository.existsById_GroupIdAndId_UserId(itinerary.getGroupId(), userId)) {
            return;
        }
        throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
    }

    private List<Integer> calculateTravelTimes(List<SpotInfo> order, String optimizationType) {
        List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(order, optimizationType);
        return extractTravelTimes(routes);
    }

    private List<Integer> extractTravelTimes(List<TransitRouteResponse> routes) {
        return routes.stream()
                .map(r -> r.options().isEmpty() ? 0 : r.options().get(0).totalTime())
                .collect(Collectors.toList());
    }

    /**
     * 시작시각 + (체류시간 60분 + 구간 이동시간) 누적으로 각 스팟 도착 예정시각 계산
     */
    private List<LocalTime> calculateArrivalTimes(LocalTime startTime, List<Integer> travelTimesBetweenSpots) {
        List<LocalTime> arrivals = new ArrayList<>();
        LocalTime current = startTime;
        arrivals.add(current);

        for (int travelMin : travelTimesBetweenSpots) {
            current = current.plusMinutes(defaultVisitDurationMinutes).plusMinutes(travelMin);
            arrivals.add(current);
        }
        return arrivals;
    }

    private GroqAdjustResult adjustWithGroq(List<SpotInfo> order, List<LocalTime> arrivalTimes) {
        String systemPrompt = """
                당신은 여행 일정의 방문 순서를 운영시간 기준으로 점검하는 도우미입니다.
                반드시 아래 JSON 형식만 출력하세요. 설명이나 마크다운 없이 순수 JSON만 출력하세요.
                {
                  "finalOrder": ["contentId1", "contentId2", ...],
                  "reason": "조정 사유를 한 문장으로 (조정이 없었다면 빈 문자열)"
                }
                """;

        StringBuilder sb = new StringBuilder();
        sb.append("## 현재 순서 (이동 거리 기준 최적 동선)\n");
        for (int i = 0; i < order.size(); i++) {
            SpotInfo spot = order.get(i);
            sb.append("- contentId: ").append(spot.getContentId())
                    .append(", 이름: ").append(spot.getName())
                    .append(", 예상 도착시각: ").append(arrivalTimes.get(i))
                    .append(", 운영시간: ").append(
                            spot.getOperatingHours() != null && !spot.getOperatingHours().isBlank()
                                    ? spot.getOperatingHours() : "정보없음")
                    .append("\n");
        }
        sb.append("\n위 순서는 이동 거리 기준으로 이미 최적화되어 있습니다. 이 순서를 최대한 유지하세요.");
        sb.append("\n단, 예상 도착시각에 운영시간이 이미 종료되어 방문이 불가능한 관광지가 있다면, ");
        sb.append("그 관광지만 마감 전에 방문할 수 있도록 순서를 앞당기세요. 그 외에는 순서를 바꾸지 마세요.");
        sb.append("\n운영시간 정보가 '정보없음'이거나 '상시 개방'인 곳은 순서 조정 대상이 아닙니다.");

        String rawResponse = groqClient.chat(systemPrompt, sb.toString());
        return parseGroqAdjustResult(rawResponse, order);
    }

    private GroqAdjustResult parseGroqAdjustResult(String rawResponse, List<SpotInfo> original) {
        try {
            String json = rawResponse.trim();
            if (json.contains("```")) {
                json = json.replaceAll("```json", "").replaceAll("```", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);

            Map<String, SpotInfo> spotMap = original.stream()
                    .collect(Collectors.toMap(SpotInfo::getContentId, s -> s));

            List<SpotInfo> order = new ArrayList<>();
            JsonNode orderNode = root.get("finalOrder");
            if (orderNode != null && orderNode.isArray()) {
                for (JsonNode idNode : orderNode) {
                    SpotInfo spot = spotMap.get(idNode.asText());
                    if (spot != null) order.add(spot);
                }
            }

            // 개수가 안 맞으면(Groq가 일부 빠뜨림) 신뢰하지 않고 원본 순서 유지
            if (order.size() != original.size()) {
                log.warn("Groq 조정 결과 스팟 개수 불일치({} → {}), 원본 순서 유지", original.size(), order.size());
                return null;
            }

            String reason = root.path("reason").asText("");
            if (reason.isBlank()) {
                reason = "이동 거리를 기준으로 동선을 최적화했어요.";
            }

            return new GroqAdjustResult(order, reason);
        } catch (Exception e) {
            log.warn("Groq 운영시간 조정 응답 파싱 실패: {}", rawResponse, e);
            return null;
        }
    }

    private void applyToEntities(List<ItineraryItem> items, List<SpotInfo> finalOrder, List<LocalTime> arrivalTimes) {
        Map<String, ItineraryItem> itemMap = items.stream()
                .collect(Collectors.toMap(item -> item.getSpot().getContentId(), item -> item));

        for (int i = 0; i < finalOrder.size(); i++) {
            SpotInfo spot = finalOrder.get(i);
            ItineraryItem item = itemMap.get(spot.getContentId());
            if (item == null) continue;
            item.update(i + 1, arrivalTimes.get(i), item.getDurationMin(),
                    item.getTravelMode(), item.getTravelTimeMin(), item.getMemo());
        }
    }

    private SpotInfo toSpotInfo(ItineraryItem item) {
        var spot = item.getSpot();
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

    private record GroqAdjustResult(List<SpotInfo> order, String reason) {}
}