package com.bujirun.bujirun.domain.spot.scheduler;

import com.bujirun.bujirun.domain.spot.service.MigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * 수동 마이그레이션 실행 API (관리자 전용)
 * POST /api/admin/migration/run
 *
 * 사용 시점: 배포 직후 초기 데이터 적재, 즉시 갱신 필요 시
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;
    private final MigrationStatusHolder statusHolder;

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run() {
        if (!statusHolder.tryStart()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 마이그레이션이 진행 중입니다."));
        }

        Mono.fromCallable(migrationService::runFullMigration)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            statusHolder.markCompleted(result);
                            log.info("마이그레이션 완료: {}", result);
                        },
                        error -> {
                            statusHolder.markFailed(error.getMessage());
                            log.error("마이그레이션 실패", error);
                        }
                );

        return ResponseEntity.accepted()
                .body(Map.of("message", "마이그레이션이 시작되었습니다. /status 로 진행 상황을 확인하세요."));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", statusHolder.getStatus());
        body.put("startedAt", statusHolder.getStartedAt());
        body.put("finishedAt", statusHolder.getFinishedAt());
        body.put("result", statusHolder.getLastResult());
        body.put("error", statusHolder.getLastError());
        return ResponseEntity.ok(body);
    }
}