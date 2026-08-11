package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.swipe.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.GroupItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.swipe.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupItineraryGenerateService {

    // 그룹원 중 이 비율 이상이 싫어요를 누르면 후보에서 완전히 제외
    private static final double DISLIKE_EXCLUDE_RATIO = 0.5;

    // planA/planB의 overlap 비율이 이 값을 초과하면(두 플랜이 대부분 동일한 스팟으로 채워졌으면) WARN 로그를 남긴다.
    // ItineraryGenerateService의 다양성 규칙(MIN_PLAN_DIFF_SPOTS)이 지켜지지 않았는지 진단하는 용도
    private static final double OVERLAP_WARN_RATIO = 0.7;

    private final SwipeResultRepository swipeResultRepository;
    private final TourSpotRepository tourSpotRepository;
    private final ItineraryGenerateService itineraryGenerateService;
    private final GroupMemberRepository groupMemberRepository;
    private final ItineraryRepository itineraryRepository;

    @Transactional(readOnly = true)
    public ItineraryGenerateResponse generateGroupItinerary(UUID groupId, GroupItineraryRequest request, UUID requesterId,
                                                             GroupPreferenceSummary groupSummary) { // 그룹원 취향 집계 (AI 추천 이유 생성용)

        if (!groupMemberRepository.existsById_GroupIdAndId_UserId(groupId, requesterId)) {
            log.warn("멤버십 체크 실패 - groupId={}, requesterId={}", groupId, requesterId);
            throw new IllegalArgumentException("그룹 멤버만 그룹 일정을 생성할 수 있습니다.");
        }

        // 여행 하나당 그룹 하나 정책 — 이미 확정된 일정이 있는 그룹은 재생성 불가
        if (itineraryRepository.existsByGroupId(groupId)) {
            throw new IllegalArgumentException("이미 이 그룹의 일정이 확정되어 있습니다.");
        }

        List<SpotSwipeAggregate> aggregates = swipeResultRepository.aggregateByGroup(groupId);

        if (aggregates.isEmpty()) {
            throw new IllegalStateException("그룹의 스와이프 결과가 없습니다. 그룹원 전원이 스와이프를 완료했는지 확인하세요.");
        }

        // spotId → TourSpot 조회 (한 번에)
        List<UUID> spotIds = aggregates.stream().map(SpotSwipeAggregate::getSpotId).toList();
        Map<UUID, TourSpot> spotMap = tourSpotRepository.findAllById(spotIds).stream()
                .collect(Collectors.toMap(TourSpot::getId, s -> s));

        List<String> likedIds = new java.util.ArrayList<>();
        List<String> dislikedIds = new java.util.ArrayList<>();

        for (SpotSwipeAggregate agg : aggregates) {
            TourSpot spot = spotMap.get(agg.getSpotId());
            if (spot == null) continue;

            double dislikeRatio = 1.0 - ((double) agg.getLikedCount() / agg.getTotalCount());

            if (dislikeRatio >= DISLIKE_EXCLUDE_RATIO) {
                dislikedIds.add(spot.getContentId());
            } else if (agg.getLikedCount() > 0) {
                // 싫어요 비율이 임계값 미만이면서 좋아요가 1개 이상이면 그룹 선호 후보로 포함
                likedIds.add(spot.getContentId());
            }
        }

        log.info("[그룹 일정 생성] groupId={}, liked={}, disliked={}", groupId, likedIds.size(), dislikedIds.size());

        // 기존 개인용 로직 재사용 - swipes를 그룹 종합 결과로 구성해서 전달
        SwipeRequest swipeRequest = buildAggregatedSwipeRequest(likedIds, dislikedIds, request);

        // 그룹 요청이지만 도감(수집 상태) 우선순위는 요청자(방장) 기준으로 반영
        ItineraryGenerateResponse response = itineraryGenerateService.generateItinerary(swipeRequest, requesterId, groupSummary); // 추가: groupSummary 전달

        // AI 추천 이유 결과를 로그로 검증하기 위한 진단 로깅
        logGroupRecommendationResult(groupId, groupSummary, response);

        return response;
    }

    // 취향 집계(categoryScore, isUniformPreference)와 AI가 생성한 추천 이유, planA/planB 다양성을 INFO/WARN으로 남긴다
    private void logGroupRecommendationResult(UUID groupId, GroupPreferenceSummary groupSummary, ItineraryGenerateResponse response) {
        log.info("[그룹 일정 생성 결과] groupId={}, 참여자수={}, categoryScore={}, isUniformPreference={}",
                groupId, groupSummary.getParticipantCount(), groupSummary.getCategoryScore(), groupSummary.isUniformPreference());

        logPlanReasons(groupId, "A", response.getPlanA());
        logPlanReasons(groupId, "B", response.getPlanB());

        checkPlanOverlap(groupId, response.getPlanA(), response.getPlanB());
    }

    // 플랜별 summaryReason과 스팟별 reasons를 로그로 남긴다 (reasons가 없는 스팟은 제외)
    private void logPlanReasons(UUID groupId, String planType, ItineraryGenerateResponse.PlanOption plan) {
        if (plan == null || plan.getDays() == null) return;

        Map<String, List<String>> spotReasons = plan.getDays().stream()
                .flatMap(d -> d.getSpots().stream())
                .filter(spot -> spot.getReasons() != null)
                .collect(Collectors.toMap(SpotInfo::getContentId, SpotInfo::getReasons, (a, b) -> a));

        log.info("[그룹 일정 생성 결과] groupId={}, plan={}, summaryReason={}, spotReasons={}",
                groupId, planType, plan.getSummaryReason(), spotReasons);
    }

    // planA/planB 스팟 overlap 개수를 항상 INFO로 남기고, 다양성 규칙 위반이 의심되면 WARN도 남긴다
    private void checkPlanOverlap(UUID groupId, ItineraryGenerateResponse.PlanOption planA, ItineraryGenerateResponse.PlanOption planB) {
        if (planA == null || planB == null || planA.getDays() == null || planB.getDays() == null) return;

        Set<String> spotsA = planA.getDays().stream()
                .flatMap(d -> d.getSpots().stream())
                .map(SpotInfo::getContentId)
                .collect(Collectors.toSet());
        Set<String> spotsB = planB.getDays().stream()
                .flatMap(d -> d.getSpots().stream())
                .map(SpotInfo::getContentId)
                .collect(Collectors.toSet());

        Set<String> overlap = new HashSet<>(spotsA);
        overlap.retainAll(spotsB);

        log.info("[그룹 일정 생성 결과] groupId={}, planA-planB overlap={}개 (planA={}개, planB={}개)",
                groupId, overlap.size(), spotsA.size(), spotsB.size());

        int largerSize = Math.max(spotsA.size(), spotsB.size());
        if (largerSize > 0 && (double) overlap.size() / largerSize > OVERLAP_WARN_RATIO) {
            log.warn("[그룹 일정 생성 결과] groupId={}, planA/planB 다양성 부족 - overlap={}개/{}개 (임계치 {}% 초과)",
                    groupId, overlap.size(), largerSize, (int) (OVERLAP_WARN_RATIO * 100));
        }
    }

    private SwipeRequest buildAggregatedSwipeRequest(List<String> likedIds, List<String> dislikedIds,
                                                     GroupItineraryRequest request) {
        List<SwipeRequest.SwipeItem> swipes = new java.util.ArrayList<>();
        likedIds.forEach(id -> swipes.add(SwipeRequest.SwipeItem.builder().contentId(id).liked(true).build()));
        dislikedIds.forEach(id -> swipes.add(SwipeRequest.SwipeItem.builder().contentId(id).liked(false).build()));

        return SwipeRequest.builder()
                .swipes(swipes)
                .startDate(request.getStartDate())
                .startTime(request.getStartTime())
                .endDate(request.getEndDate())
                .endTime(request.getEndTime())
                .optimizationType(request.getOptimizationType())
                .build();
    }
}