package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.GroupItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupItineraryGenerateService {

    // 그룹원 중 이 비율 이상이 싫어요를 누르면 후보에서 완전히 제외
    private static final double DISLIKE_EXCLUDE_RATIO = 0.5;

    private final SwipeResultRepository swipeResultRepository;
    private final TourSpotRepository tourSpotRepository;
    private final ItineraryGenerateService itineraryGenerateService;

    public ItineraryGenerateResponse generateGroupItinerary(UUID groupId, GroupItineraryRequest request, UUID requesterId) {

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
        return itineraryGenerateService.generateItinerary(swipeRequest, requesterId);
    }

    private SwipeRequest buildAggregatedSwipeRequest(List<String> likedIds, List<String> dislikedIds,
                                                     GroupItineraryRequest request) {
        List<SwipeRequest.SwipeItem> swipes = new java.util.ArrayList<>();
        likedIds.forEach(id -> swipes.add(SwipeRequest.SwipeItem.builder().contentId(id).liked(true).build()));
        dislikedIds.forEach(id -> swipes.add(SwipeRequest.SwipeItem.builder().contentId(id).liked(false).build()));

        return SwipeRequest.builder()
                .swipes(swipes)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .optimizationType(request.getOptimizationType())
                .build();
    }
}