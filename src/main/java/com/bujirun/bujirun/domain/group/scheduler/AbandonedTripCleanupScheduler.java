package com.bujirun.bujirun.domain.group.scheduler;

import com.bujirun.bujirun.domain.group.service.AbandonedTripCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스와이프/투표 단계에서 방치된("생성 중 이탈") 여행(그룹)을 매일 정리하는 배치.
 * 자세한 정리 규칙은 {@link AbandonedTripCleanupService} 참고.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedTripCleanupScheduler {

    private final AbandonedTripCleanupService abandonedTripCleanupService;

    // 매일 새벽 4시 30분 (계정 개인정보 정리 배치 다음)
    @Scheduled(cron = "0 30 4 * * *")
    public void purgeAbandonedTrips() {
        try {
            int deleted = abandonedTripCleanupService.purgeAbandonedTrips();
            log.info("[중도 이탈 여행 정리] 배치 완료 - {}건", deleted);
        } catch (Exception e) {
            // 배치 실패가 애플리케이션 전체에 영향을 주지 않도록 삼킨다 (다음 날 재시도).
            log.error("[중도 이탈 여행 정리] 배치 실패", e);
        }
    }
}
