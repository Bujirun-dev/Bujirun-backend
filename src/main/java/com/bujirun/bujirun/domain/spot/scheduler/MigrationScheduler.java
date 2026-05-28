package com.bujirun.bujirun.domain.spot.scheduler;

import com.bujirun.bujirun.domain.spot.service.MigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 관광지 데이터 주기적 갱신 스케줄러
 * 매주 월요일 새벽 3시 자동 실행 (운영시간·휴무 변경 반영)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationScheduler {

    private final MigrationService migrationService;

    // 목요일 새벽 3시
    @Scheduled(cron = "0 0 3 * * THU")
    public void scheduledMigration() {
        log.info("[Scheduler] 주간 관광지 데이터 갱신 시작" );
        migrationService.runFullMigration();
    }
}