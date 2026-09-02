package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * "생성 중 이탈"로 방치된 여행(그룹) 정리.
 *
 * <p>부지런의 모든 여행은 {@code groups} 생성으로 시작해서 초대 → 스와이프 → 투표 → 확정(finalize)
 * 순서로 진행되고, {@code itineraries} 행은 확정 시점에만 만들어진다. 스와이프만 하다 나가거나
 * 투표 단계에서 투표를 안 한 채 앱을 떠나면 {@code groups}/{@code group_members}/{@code swipe_sessions}/
 * {@code itinerary_vote_sessions} 만 남고 확정된 일정은 없는 "껍데기 여행"이 계속 쌓인다.
 * (트립 목록에는 확정된 일정만 노출되므로 사용자 눈에는 안 보이고 DB에만 남는다.)
 *
 * <p>일정이 확정된 적이 없고 {@link #abandonThreshold} 기간 동안 아무 활동도 없었던 그룹을
 * 삭제한다. {@code groups} 삭제 시 DB FK 옵션에 따라 {@code group_members}(CASCADE),
 * {@code itinerary_vote_sessions}·{@code itinerary_votes}(CASCADE)가 함께 지워지고,
 * {@code swipe_sessions.group_id}는 SET NULL 처리되어 스와이프 기록 자체는 사용자 소유로 보존된다
 * (그룹 나가기 로직 {@code GroupService#leave}와 동일한 정리 규칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbandonedTripCleanupService {

    /**
     * 이 기간 동안 스와이프/투표/멤버 합류 등 아무 활동도 없고 일정도 확정되지 않은 그룹은
     * "생성 중 이탈"로 본다. 친구 초대 후 며칠 뒤 합류·스와이프하는 그룹 여행 특성상 넉넉하게 잡았다.
     * 더 공격적으로 정리하려면 이 값만 줄이면 된다.
     */
    private static final Duration ABANDON_THRESHOLD = Duration.ofDays(7);

    /** 한 번의 DELETE ... IN (...) 에 넣을 최대 그룹 수 (파라미터 폭주 방지). */
    private static final int DELETE_BATCH_SIZE = 500;

    private final GroupRepository groupRepository;

    /**
     * 방치된 여행(그룹)을 정리하고 삭제한 그룹 수를 반환한다.
     */
    @Transactional
    public int purgeAbandonedTrips() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ABANDON_THRESHOLD);
        List<UUID> abandonedGroupIds = groupRepository.findAbandonedGroupIds(cutoff);
        if (abandonedGroupIds.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < abandonedGroupIds.size(); i += DELETE_BATCH_SIZE) {
            List<UUID> chunk = abandonedGroupIds.subList(
                    i, Math.min(i + DELETE_BATCH_SIZE, abandonedGroupIds.size()));
            groupRepository.deleteAllByIdInBatch(chunk);
        }

        log.info("[중도 이탈 여행 정리] 방치 기준 {}일, 그룹 {}개 삭제: {}",
                ABANDON_THRESHOLD.toDays(), abandonedGroupIds.size(), abandonedGroupIds);
        return abandonedGroupIds.size();
    }
}
