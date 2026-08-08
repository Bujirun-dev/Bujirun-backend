package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.swipe.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 그룹 스와이프 세션 참여자 전원의 좋아요를 카테고리 기준으로 집계한다. AI 호출 없는 순수 백엔드 계산.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupPreferenceService {

    private final SwipeResultRepository swipeResultRepository;
    private final SwipeSessionRepository swipeSessionRepository;
    private final TourSpotRepository tourSpotRepository;

    public GroupPreferenceSummary summarize(UUID groupId) {
        List<SpotSwipeAggregate> aggregates = swipeResultRepository.aggregateByGroup(groupId);

        Map<UUID, TourSpot> spotMap = tourSpotRepository
                .findAllById(aggregates.stream().map(SpotSwipeAggregate::getSpotId).toList())
                .stream()
                .collect(Collectors.toMap(TourSpot::getId, s -> s));

        Map<String, Long> categoryCounts = aggregates.stream()
                .filter(a -> spotMap.get(a.getSpotId()) != null && spotMap.get(a.getSpotId()).getCategory() != null)
                .collect(Collectors.groupingBy(
                        a -> spotMap.get(a.getSpotId()).getCategory(),
                        Collectors.summingLong(SpotSwipeAggregate::getLikedCount)));

        long participantCount = swipeSessionRepository.countDistinctCompletedUsersByGroupId(groupId);

        return GroupPreferenceSummary.builder()
                .participantCount((int) participantCount)
                .categoryCounts(categoryCounts)
                .build();
    }
}
