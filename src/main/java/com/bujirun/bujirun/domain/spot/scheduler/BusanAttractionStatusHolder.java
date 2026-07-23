package com.bujirun.bujirun.domain.spot.scheduler;

import com.bujirun.bujirun.domain.spot.service.MigrationService;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 부산명소정보 API 연동(enrichWithBusanAttractionApi) 실행 상태를 메모리에 보관.
 * 서버 재시작 시 초기화됨 (단발성 확인 용도).
 */
@Component
@Getter
public class BusanAttractionStatusHolder {

    private volatile MigrationStatus status = MigrationStatus.IDLE;
    private volatile MigrationService.BusanEnrichResult lastResult;
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

    public void markCompleted(MigrationService.BusanEnrichResult result) {
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
