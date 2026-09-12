package com.bujirun.bujirun.domain.itinerary.optimize.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.client.OpenAiClient;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitDetail;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import com.bujirun.bujirun.domain.itinerary.generate.service.SpotOrderOptimizer;
import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayScheduleMappingService;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.request.ItineraryOptimizeRequest;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.response.ItineraryOptimizeResponse;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryDayRepository;
import com.bujirun.bujirun.global.util.ItineraryTimeUtils;
import com.bujirun.bujirun.global.util.TransitRouteUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItineraryOptimizeService {

    private final ItineraryDayRepository itineraryDayRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TransitRouteService transitRouteService;
    private final SubwayScheduleMappingService subwayScheduleMappingService;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    @Value("${itinerary.default-visit-duration-minutes:60}")
    private int defaultVisitDurationMinutes;

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

        // 시작 기준 시각: ①명시적으로 요청된 값 → ②그 day의 기존 첫 항목 시각(사용자가 이미
        // 정해둔 시각을 최적화가 마음대로 옮기지 않도록 존중) → ③기준 기본값. ③은 첫날이면
        // 여행 시작 시각, 둘째 날 이후면 09:00이다 — 예전엔 무조건 09:00 하드코딩이라
        // "20:00 도착" 여행의 첫날도 09:00부터 계산했다.
        Itinerary itinerary = day.getItinerary();
        int totalDays = itinerary.getDays().size();
        LocalTime startTime = request.getStartTime() != null
                ? request.getStartTime()
                : (items.get(0).getArrivalTime() != null
                        ? items.get(0).getArrivalTime()
                        : ItineraryTimeUtils.resolveDayStartTime(day.getDayNumber(), itinerary.getStartTime()));
        // 도착 시각 상한. 마지막 날에는 여행 종료 시각을, 그 외의 날엔 자정 직전을 상한으로 쓴다.
        LocalTime dayEndLimit = ItineraryTimeUtils.resolveDayEndLimit(
                day.getDayNumber(), totalDays, itinerary.getEndTime());

        // 1차: 좌표 기반 nearest-neighbor 재정렬
        List<SpotInfo> baseOrder = SpotOrderOptimizer.sortByNearestNeighbor(spots);

        // 2차: 운영시간 고려해서 OpenAI한테 최종 순서 조정 요청
        List<SpotInfo> finalOrder = baseOrder;
        String reason = "이동 거리를 기준으로 동선을 최적화했어요.";

        boolean hasOperatingHours = baseOrder.stream()
                .anyMatch(s -> s.getOperatingHours() != null && !s.getOperatingHours().isBlank());

        if (hasOperatingHours) {
            List<Integer> travelTimes = calculateTravelTimes(baseOrder, request.getOptimizationType());
            List<LocalTime> arrivalTimes = calculateArrivalTimes(startTime, travelTimes, dayEndLimit);

            try {
                OpenAiAdjustResult adjusted = adjustWithOpenAi(baseOrder, arrivalTimes);
                if (adjusted != null && !adjusted.order().isEmpty()) {
                    finalOrder = adjusted.order();
                    reason = adjusted.reason();
                }
            } catch (Exception e) {
                log.warn("OpenAI 운영시간 조정 실패, nearest-neighbor 순서 그대로 사용: {}", e.getMessage());
            }
        }

        // 최종 순서로 구간 경로 + 도착시각 재계산
        List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(finalOrder, request.getOptimizationType());
        List<Integer> finalTravelTimes = extractTravelTimes(routes);
        List<LocalTime> finalArrivalTimes = calculateArrivalTimes(startTime, finalTravelTimes, dayEndLimit);

        // 결과를 ItineraryItem에 반영
        Map<String, ItineraryItem> itemMap = items.stream()
                .collect(Collectors.toMap(item -> item.getSpot().getContentId(), item -> item));
        applyToEntities(itemMap, finalOrder, finalArrivalTimes, routes);

        // 응답의 교통정보 필드는 ItineraryItemResponse와 동일하게, applyToEntities가 방금 반영한 엔티티 값을 그대로 담는다
        List<ItineraryOptimizeResponse.OptimizedSpot> optimizedSpots = new ArrayList<>();
        for (int i = 0; i < finalOrder.size(); i++) {
            SpotInfo spot = finalOrder.get(i);
            ItineraryItem item = itemMap.get(spot.getContentId());
            optimizedSpots.add(ItineraryOptimizeResponse.OptimizedSpot.builder()
                    .contentId(spot.getContentId())
                    .name(spot.getName())
                    // 저장되는 order_index와 같은 값(0-based)을 그대로 내려준다 — 프론트는 이
                    // 값으로 오름차순 정렬만 하므로 기준을 DB와 일치시키는 편이 안전하다.
                    .order(i)
                    .arrivalTime(finalArrivalTimes.get(i))
                    .travelMode(item != null ? item.getTravelMode() : null)
                    .travelTimeMin(item != null ? item.getTravelTimeMin() : null)
                    .routeType(item != null ? item.getRouteType() : null)
                    .routeNo(item != null ? item.getRouteNo() : null)
                    .startStationName(item != null ? item.getStartStationName() : null)
                    .endStationName(item != null ? item.getEndStationName() : null)
                    .startArsId(item != null ? item.getStartArsId() : null)
                    .transitDetail(item != null ? item.getTransitDetail() : null)
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
     * 시작시각 + (체류시간 60분 + 구간 이동시간) 누적으로 각 스팟 도착 예정시각 계산.
     *
     * 예전엔 여기서 LocalTime.plusMinutes를 그대로 누적했는데, LocalTime은 자정을 넘기면
     * 00:20처럼 한 바퀴 돌아버려서 늦은 시각 일정이 새벽 시각으로 저장됐다. 종료 시각 상한도
     * 없었다. 두 규칙(자정 차단 + 종료 시각 상한 + 같은 날 시각 중복 방지)은 투표 확정·시작시각
     * 변경과 같아야 하므로 ItineraryTimeUtils로 모아서 공유한다.
     */
    private List<LocalTime> calculateArrivalTimes(LocalTime startTime, List<Integer> travelTimesBetweenSpots,
                                                  LocalTime dayEndLimit) {
        List<Integer> gaps = travelTimesBetweenSpots.stream()
                .map(travelMin -> defaultVisitDurationMinutes + (travelMin == null ? 0 : travelMin))
                .toList();
        return ItineraryTimeUtils.accumulateArrivalTimes(startTime, gaps, dayEndLimit);
    }

    private OpenAiAdjustResult adjustWithOpenAi(List<SpotInfo> order, List<LocalTime> arrivalTimes) {
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

        String rawResponse = openAiClient.chat(systemPrompt, sb.toString());
        return parseOpenAiAdjustResult(rawResponse, order);
    }

    private OpenAiAdjustResult parseOpenAiAdjustResult(String rawResponse, List<SpotInfo> original) {
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

            // 개수가 안 맞으면(OpenAI가 일부 빠뜨림) 신뢰하지 않고 원본 순서 유지
            if (order.size() != original.size()) {
                log.warn("OpenAI 조정 결과 스팟 개수 불일치({} → {}), 원본 순서 유지", original.size(), order.size());
                return null;
            }

            String reason = root.path("reason").asText("");
            if (reason.isBlank()) {
                reason = "이동 거리를 기준으로 동선을 최적화했어요.";
            }

            return new OpenAiAdjustResult(order, reason);
        } catch (Exception e) {
            log.warn("OpenAI 운영시간 조정 응답 파싱 실패: {}", rawResponse, e);
            return null;
        }
    }

    private void applyToEntities(Map<String, ItineraryItem> itemMap, List<SpotInfo> finalOrder, List<LocalTime> arrivalTimes,
                                 List<TransitRouteResponse> routes) {
        for (int i = 0; i < finalOrder.size(); i++) {
            SpotInfo spot = finalOrder.get(i);
            ItineraryItem item = itemMap.get(spot.getContentId());
            if (item == null) continue;

            TransitOption leg = (i == 0 || routes.get(i - 1).options().isEmpty())
                    ? null
                    : routes.get(i - 1).options().get(0);

            SubPath firstTransitSubPath = leg != null
                    ? TransitRouteUtils.findFirstTransitSubPath(leg.subPaths())
                    : null;

            TransitDetail transitDetail = leg != null
                    ? TransitDetail.from(leg, subwayScheduleMappingService.mapSubwaySegments(leg))
                    : TransitDetail.EMPTY;

            // 순서/도착시각/체류시간/메모 갱신
            // orderIndex는 0부터 — reorderItems·프론트 addItem이 0-based인데 여기만 1부터
            // 넣어서, 최적화 직후 순서 기준이 두 갈래로 갈렸다.
            item.update(i, arrivalTimes.get(i), item.getDurationMin(),
                    leg != null ? TransitRouteUtils.toTravelMode(leg.type()) : null,
                    leg != null ? leg.totalTime() : null,
                    item.getMemo());

            // 경로 상세(노선번호·정류장명 등) 갱신
            // routeType: subPath 실측 타입(버스/지하철) 우선, 없으면(도보/택시 옵션) 옵션 타입 그대로
            item.updateRoute(
                    leg != null ? TransitRouteUtils.toTravelMode(leg.type()) : null,
                    leg != null ? leg.totalTime() : null,
                    firstTransitSubPath != null ? firstTransitSubPath.type() : (leg != null ? leg.type() : null),
                    firstTransitSubPath != null ? firstTransitSubPath.routeNo() : null,
                    firstTransitSubPath != null ? firstTransitSubPath.startName() : null,
                    firstTransitSubPath != null ? firstTransitSubPath.endName() : null,
                    firstTransitSubPath != null ? firstTransitSubPath.startArsId() : null,
                    transitDetail
            );
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

    private record OpenAiAdjustResult(List<SpotInfo> order, String reason) {}
}