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
    private final BusanAttractionStatusHolder busanAttractionStatusHolder;
    private final SummarizeStatusHolder summarizeStatusHolder;

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

    // 부산광역시_부산명소정보 API(data.go.kr 15063481)로 관광지 소개정보(부제목·상세내용·교통정보·휴무일·이용요금) 보완
    @PostMapping("/busan-attraction/run")
    public ResponseEntity<Map<String, String>> runBusanAttraction() {
        if (!busanAttractionStatusHolder.tryStart()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 부산명소정보 연동이 진행 중입니다."));
        }

        Mono.fromCallable(migrationService::enrichWithBusanAttractionApi)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            busanAttractionStatusHolder.markCompleted(result);
                            log.info("부산명소정보 연동 완료: {}", result);
                        },
                        error -> {
                            busanAttractionStatusHolder.markFailed(error.getMessage());
                            log.error("부산명소정보 연동 실패", error);
                        }
                );

        return ResponseEntity.accepted()
                .body(Map.of("message", "부산명소정보 연동이 시작되었습니다. /busan-attraction/status 로 진행 상황을 확인하세요."));
    }

    @GetMapping("/busan-attraction/status")
    public ResponseEntity<Map<String, Object>> busanAttractionStatus() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", busanAttractionStatusHolder.getStatus());
        body.put("startedAt", busanAttractionStatusHolder.getStartedAt());
        body.put("finishedAt", busanAttractionStatusHolder.getFinishedAt());
        body.put("result", busanAttractionStatusHolder.getLastResult());
        body.put("error", busanAttractionStatusHolder.getLastError());
        return ResponseEntity.ok(body);
    }

    // 부산명소정보 API로 채워진 소개글(description)이 너무 길다는 피드백에 따라 Groq로 2~3문장 재요약
    @PostMapping("/busan-attraction/summarize")
    public ResponseEntity<Map<String, String>> summarizeBusanDescriptions() {
        if (!summarizeStatusHolder.tryStart()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "이미 소개글 요약이 진행 중입니다."));
        }

        Mono.fromCallable(migrationService::summarizeBusanDescriptions)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            summarizeStatusHolder.markCompleted(result);
                            log.info("소개글 요약 완료: {}", result);
                        },
                        error -> {
                            summarizeStatusHolder.markFailed(error.getMessage());
                            log.error("소개글 요약 실패", error);
                        }
                );

        return ResponseEntity.accepted()
                .body(Map.of("message", "소개글 요약이 시작되었습니다. /busan-attraction/summarize/status 로 진행 상황을 확인하세요."));
    }

    @GetMapping("/busan-attraction/summarize/status")
    public ResponseEntity<Map<String, Object>> summarizeStatus() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", summarizeStatusHolder.getStatus());
        body.put("startedAt", summarizeStatusHolder.getStartedAt());
        body.put("finishedAt", summarizeStatusHolder.getFinishedAt());
        body.put("result", summarizeStatusHolder.getLastResult());
        body.put("error", summarizeStatusHolder.getLastError());
        return ResponseEntity.ok(body);
    }
}