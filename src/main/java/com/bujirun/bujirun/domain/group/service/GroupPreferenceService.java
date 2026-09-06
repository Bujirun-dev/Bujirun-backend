package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.swipe.dto.projection.UserCategoryLike;
import com.bujirun.bujirun.domain.swipe.dto.projection.UserSwipeAggregate;
import com.bujirun.bujirun.domain.swipe.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 그룹 스와이프 세션 참여자 전원의 좋아요를 카테고리 기준으로 집계한다. AI 호출 없는 순수 백엔드 계산.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupPreferenceService {

    // categoryScore 1위-2위의 상대 격차가 이 값 미만이면 편중되지 않은(uniform) 취향으로 판단
    private static final double UNIFORM_GAP_RATIO = 0.2;

    private final SwipeResultRepository swipeResultRepository;
    private final SwipeSessionRepository swipeSessionRepository;
    private final TourSpotRepository tourSpotRepository;

    public GroupPreferenceSummary summarize(UUID groupId) {
        List<SpotSwipeAggregate> aggregates = swipeResultRepository.aggregateByGroup(groupId);

        Map<UUID, TourSpot> spotMap = tourSpotRepository
                .findAllById(aggregates.stream().map(SpotSwipeAggregate::getSpotId).toList())
                .stream()
                .collect(Collectors.toMap(TourSpot::getId, s -> s));

        // categoryScore(findLikedCategoriesByGroup)와 같은 분류 체계(collectionCategory)를 써야
        // AI 추천 이유 문구에서 두 지표가 어긋나지 않는다
        Map<String, Long> categoryCounts = aggregates.stream()
                .filter(a -> spotMap.get(a.getSpotId()) != null && spotMap.get(a.getSpotId()).getCollectionCategory() != null)
                .collect(Collectors.groupingBy(
                        a -> spotMap.get(a.getSpotId()).getCollectionCategory(),
                        Collectors.summingLong(SpotSwipeAggregate::getLikedCount)));

        long participantCount = swipeSessionRepository.countDistinctCompletedUsersByGroupId(groupId);

        // 참여자별 selectivity 가중치를 반영한 categoryScore 계산 (단순 count가 아닌 가중합)
        Map<UUID, Double> selectivityByUser = swipeResultRepository.aggregateUserStatsByGroup(groupId).stream()
                .collect(Collectors.toMap(UserSwipeAggregate::getUserId, this::calculateSelectivity));

        Map<String, Double> categoryScore = swipeResultRepository.findLikedCategoriesByGroup(groupId).stream()
                .collect(Collectors.groupingBy(
                        UserCategoryLike::getCategory,
                        Collectors.summingDouble(ucl -> selectivityByUser.getOrDefault(ucl.getUserId(), 0.0))));

        boolean isUniformPreference = calculateIsUniformPreference(categoryScore);

        return GroupPreferenceSummary.builder()
                .participantCount((int) participantCount)
                .categoryCounts(categoryCounts)
                .categoryScore(categoryScore)
                .isUniformPreference(isUniformPreference)
                .build();
    }

    // 전체 스와이프 중 좋아요 비율이 낮을수록(까다롭게 골랐을수록) selectivity가 높다
    // 모든 곳에 좋아요를 누른 참여자는 selectivity가 0에 가까워 가중치가 낮아진다
    private double calculateSelectivity(UserSwipeAggregate stats) {
        if (stats.getTotalCount() == null || stats.getTotalCount() == 0) return 0.0;
        return 1.0 - ((double) stats.getLikedCount() / stats.getTotalCount());
    }

    // categoryScore 1위와 2위의 상대 격차가 임계치 미만이면 특정 카테고리로 쏠리지 않은 것으로 판단
    // 유효한 좋아요 데이터가 전혀 없으면(top1=0) 근거로 삼을 카테고리가 없으므로 uniform으로 취급한다
    private boolean calculateIsUniformPreference(Map<String, Double> categoryScore) {
        List<Double> sorted = categoryScore.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        double top1 = sorted.isEmpty() ? 0.0 : sorted.get(0);
        double top2 = sorted.size() > 1 ? sorted.get(1) : 0.0;

        if (top1 <= 0.0) return true;

        double relativeGap = (top1 - top2) / top1;
        return relativeGap < UNIFORM_GAP_RATIO;
    }
}
