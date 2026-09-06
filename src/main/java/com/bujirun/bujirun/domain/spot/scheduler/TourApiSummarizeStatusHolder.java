package com.bujirun.bujirun.domain.spot.scheduler;

import com.bujirun.bujirun.domain.spot.service.MigrationService;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * TourAPI 개요(overview) 소개글 요약(summarizeTourApiOverviewDescriptions) 실행 상태를 메모리에 보관.
 * 서버 재시작 시 초기화됨 (단발성 확인 용도).
 */
@Component
@Getter
public class TourApiSummarizeStatusHolder {

    private volatile MigrationStatus status = MigrationStatus.IDLE;
    private volatile MigrationService.SummarizeResult lastResult;
    private volatile String lastError;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;

    public synchronized boolean tryStart() {
        if (status == MigrationStatus.RUNNING) {
            return false;
        }
        status = MigrationStatus.RUNNING;
        startedAt = LocalDateTime.now();
        lastError = null;
        return true;
    }

    public void markCompleted(MigrationService.SummarizeResult result) {
        this.lastResult = result;
        this.status = MigrationStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.lastError = errorMessage;
        this.status = MigrationStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
    }
}
