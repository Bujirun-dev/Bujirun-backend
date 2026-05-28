package com.bujirun.bujirun.domain.spot.scheduler;

import com.bujirun.bujirun.domain.spot.service.MigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 수동 마이그레이션 실행 API (관리자 전용)
 * POST /admin/migration/run
 *
 * 사용 시점: 배포 직후 초기 데이터 적재, 즉시 갱신 필요 시
 */
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping("/run")
    public Mono<ResponseEntity<MigrationService.MigrationResult>> run() {
        return Mono.fromCallable(migrationService::runFullMigration)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}